package com.viomanager.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.KbDocStatus;
import com.viomanager.common.enums.KbSourceType;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.kb.dto.KbAdminQueryDTO;
import com.viomanager.kb.dto.KbDocCreateDTO;
import com.viomanager.kb.dto.KbDocQueryDTO;
import com.viomanager.kb.dto.KbDocUpdateDTO;
import com.viomanager.kb.entity.KbDoc;
import com.viomanager.kb.mapper.KbDocMapper;
import com.viomanager.kb.service.KbDocService;
import com.viomanager.kb.support.KbDocAccessSupport;
import com.viomanager.kb.support.KbDocQuerySupport;
import com.viomanager.kb.support.KbRecommendCache;
import com.viomanager.kb.support.RagNotifyClient;
import com.viomanager.kb.vo.KbAdminDocVO;
import com.viomanager.kb.vo.KbDocDetailVO;
import com.viomanager.kb.vo.KbDocVO;
import com.viomanager.kb.vo.KbPromptVO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 知识库文档写口实现（KB12-16）；读口（KB01-04/KB17）全部委托 KbDocQuerySupport。
 * 并发对策（方案 §4）：计数/置顶/状态全部原子或条件 SQL，编辑用 updated_at 乐观锁（1007），
 * 所有写操作后失效推荐位缓存。
 */
@Service
public class KbDocServiceImpl implements KbDocService {

    @Resource
    private KbDocMapper kbDocMapper;

    @Resource
    private KbDocAccessSupport accessSupport;

    @Resource
    private KbDocQuerySupport querySupport;

    @Resource
    private KbRecommendCache recommendCache;

    @Resource
    private RagNotifyClient ragNotifyClient;

    // ==================== 读口（委托） ====================

    @Override
    public Result<List<KbDocVO>> list(KbDocQueryDTO dto) {
        return Result.ok(querySupport.list(dto));
    }

    @Override
    public Result<KbDocDetailVO> detail(Long id) {
        return Result.ok(querySupport.detail(id));
    }

    @Override
    public Result<List<KbDocVO>> recommend(Integer limit) {
        return Result.ok(querySupport.recommend(limit));
    }

    @Override
    public Result<List<KbPromptVO>> prompts() {
        return Result.ok(querySupport.prompts());
    }

    @Override
    public Result<PageResult<KbAdminDocVO>> adminList(KbAdminQueryDTO dto) {
        return Result.ok(querySupport.adminList(dto));
    }

    // ==================== 写口 ====================

    /** 分类拍板砍掉后的占位默认值（列 NOT NULL；正式需求加回分类时再开入参） */
    private static final String CATEGORY_DEFAULT = "未分类";

    /** KB12 发布/存草稿。形态校验：LINK 必填 linkUrl；FILE 必填 fileId+fileName；
     *  MANUAL 必填 content（2026-10-26 RAG 拆步 2 加回正文列，语料走此形态） */
    @Override
    public Result<KbDocVO> create(KbDocCreateDTO dto) {
        //1.形态校验：LINK / FILE / MANUAL
        String sourceType = dto.getSourceType();
        boolean isLink = KbSourceType.LINK.name().equals(sourceType);
        boolean isFile = KbSourceType.FILE.name().equals(sourceType);
        boolean isManual = KbSourceType.MANUAL.name().equals(sourceType);
        if (!isLink && !isFile && !isManual) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "sourceType 只能是 LINK、FILE 或 MANUAL");
        }
        if (isManual && !StringUtils.hasText(dto.getContent())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "MANUAL 条目必填 content（markdown 正文）");
        }
        if (isLink && !StringUtils.hasText(dto.getLinkUrl())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "LINK 条目必填 linkUrl");
        }
        if (isFile) {
            if (dto.getFileId() == null) {
                throw new BizException(CommonErrorCode.PARAM_INVALID, "FILE 条目必填 fileId");
            }
            if (!StringUtils.hasText(dto.getFileName())) {
                throw new BizException(CommonErrorCode.PARAM_INVALID, "FILE 条目必填 fileName");
            }
        }
        //2.落库（创建只能直接发布或存草稿；上下架/归档走 KB16）
        String status = StringUtils.hasText(dto.getStatus())
                ? dto.getStatus() : KbDocStatus.PUBLISHED.name();
        if (!KbDocStatus.DRAFT.name().equals(status)
                && !KbDocStatus.PUBLISHED.name().equals(status)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "目标状态只能是 DRAFT 或 PUBLISHED");
        }
        CurrentUser user = UserHolder.getUser();
        KbDoc doc = new KbDoc();
        doc.setTitle(dto.getTitle());
        doc.setCategory(CATEGORY_DEFAULT);
        doc.setIsPrompt(0);
        doc.setSummary(dto.getSummary());
        // MANUAL 正文（RAG 切片语料）；LINK/FILE 的 content 维持闲置口径不收
        doc.setContent(isManual ? dto.getContent() : null);
        doc.setSourceType(sourceType);
        doc.setSourceUrl(isLink ? dto.getLinkUrl() : null);
        doc.setAttachFileId(isFile ? dto.getFileId() : null);
        doc.setFileName(isFile ? dto.getFileName() : null);
        // 发布人姓名取 JWT 快照（独立库无用户表，V2 冗余列）
        doc.setPublisherName(user.getRealName());
        doc.setStatus(status);
        doc.setTopFlag(0);
        doc.setViewCount(0);
        doc.setCollectCount(0);
        doc.setCommentCount(0);
        // 归属组织缺省取当前登录人组织（管理端归属标记，浏览侧不按此过滤）
        doc.setOrgId(dto.getOrgId() != null ? dto.getOrgId() : user.getOrgId());
        // created_by 基座暂不自动填充（同 FileServiceImpl 口径，手工置）
        doc.setCreatedBy(user.getUserId());
        doc.setUpdatedBy(user.getUserId());
        kbDocMapper.insert(doc);
        //3.推荐位失效（新发布/新草稿都可能改变推荐集）
        recommendCache.invalidate();
        //4.RAG 薄通知：直接发布的文档进向量索引（草稿等发布时再进）
        if (KbDocStatus.PUBLISHED.name().equals(status)) {
            ragNotifyClient.notifyDocAsync(doc);
        }
        return Result.ok(querySupport.toVO(doc));
    }

    /** KB13 编辑（标题/摘要/链接/换文件）。带 updatedAt 即乐观锁：0 行命中 1007（规范预留码） */
    @Override
    public Result<Void> update(Long id, KbDocUpdateDTO dto) {
        KbDoc existing = accessSupport.requireExists(id);
        //LINK 条目不允许清空唯一链接
        if (dto.getLinkUrl() != null && !StringUtils.hasText(dto.getLinkUrl())
                && KbSourceType.LINK.name().equals(existing.getSourceType())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "LINK 条目不能清空 linkUrl");
        }
        //换文件必须成对提供 fileId 与 fileName，避免半截数据
        if ((dto.getFileId() != null) != StringUtils.hasText(dto.getFileName())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "fileId 与 fileName 必须成对提供");
        }
        //条件更新：WHERE 带旧 updated_at（若提供），SET 只改传入字段
        UpdateWrapper<KbDoc> uw = new UpdateWrapper<>();
        uw.eq("id", id);
        if (dto.getUpdatedAt() != null) {
            uw.eq("updated_at", dto.getUpdatedAt());
        }
        uw.set(dto.getTitle() != null, "title", dto.getTitle())
                .set(dto.getSummary() != null, "summary", dto.getSummary())
                .set(dto.getContent() != null, "content", dto.getContent())
                .set(dto.getLinkUrl() != null, "source_url", dto.getLinkUrl())
                .set(dto.getFileId() != null, "attach_file_id", dto.getFileId())
                .set(dto.getFileName() != null, "file_name", dto.getFileName())
                .set("updated_by", UserHolder.getUser().getUserId());
        int rows = kbDocMapper.update(null, uw);
        if (rows == 0) {
            if (dto.getUpdatedAt() != null) {
                throw new BizException(CommonErrorCode.CONCURRENT_MODIFIED);
            }
            throw new BizException(CommonErrorCode.NOT_FOUND, "文档不存在");
        }
        recommendCache.invalidate();
        //发布态文档编辑即重切（标题/摘要/正文都可能影响检索）
        KbDoc current = accessSupport.requireExists(id);
        if (KbDocStatus.PUBLISHED.name().equals(current.getStatus())) {
            ragNotifyClient.notifyDocAsync(current);
        }
        return Result.ok();
    }

    /** KB14 逻辑删除：评论收藏不级联清（随文档整体不可见，清单 §5）；RAG 索引同步清 */
    @Override
    public Result<Void> delete(Long id) {
        accessSupport.requireExists(id);
        kbDocMapper.deleteById(id);
        recommendCache.invalidate();
        ragNotifyClient.notifyRemoveAsync(id);
        return Result.ok();
    }

    /** KB15 置顶/取消置顶：单条 SQL 原子翻转，连点/并发不会翻乱 */
    @Override
    public Result<Void> toggleTop(Long id) {
        accessSupport.requireExists(id);
        kbDocMapper.toggleTop(id, UserHolder.getUser().getUserId());
        recommendCache.invalidate();
        return Result.ok();
    }

    /** KB16 状态流转：DRAFT→PUBLISHED（发布）、PUBLISHED↔OFFLINE；条件 UPDATE 防并发跳档 */
    @Override
    public Result<Void> updateStatus(Long id, String target) {
        KbDoc doc = accessSupport.requireExists(id);
        if (!KbDocStatus.PUBLISHED.name().equals(target)
                && !KbDocStatus.OFFLINE.name().equals(target)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "目标状态只能是 PUBLISHED 或 OFFLINE");
        }
        String from = doc.getStatus();
        if (from.equals(target)) {
            return Result.ok();
        }
        boolean allowed = (KbDocStatus.DRAFT.name().equals(from) && KbDocStatus.PUBLISHED.name().equals(target))
                || (KbDocStatus.PUBLISHED.name().equals(from) && KbDocStatus.OFFLINE.name().equals(target))
                || (KbDocStatus.OFFLINE.name().equals(from) && KbDocStatus.PUBLISHED.name().equals(target));
        if (!allowed) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED,
                    "状态不允许从 " + from + " 流转到 " + target);
        }
        int rows = kbDocMapper.casStatus(id, from, target, UserHolder.getUser().getUserId());
        if (rows == 0) {
            //并发下状态已被别人改走——按状态不允许处理，前端引导刷新
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "文档状态已变化，请刷新后重试");
        }
        recommendCache.invalidate();
        //RAG 薄通知：上架=重切索引（拿最新全文），下架=清索引
        if (KbDocStatus.PUBLISHED.name().equals(target)) {
            ragNotifyClient.notifyDocAsync(accessSupport.requireExists(id));
        } else {
            ragNotifyClient.notifyRemoveAsync(id);
        }
        return Result.ok();
    }

    /** 管理端全量重建 RAG 索引（拆步 2 兜底）：AI 掉线期间漏通知的文档一键补齐。
     *  逐篇同步推送（每篇一次全文重切 embedding，秒级/篇），几十篇内一次性返回汇总。
     *  刻意 SELECT content——实体注释禁的是浏览列表热路径，本管理操作必须拿正文。 */
    @Override
    public Result<Map<String, Object>> rebuildRagIndex() {
        List<KbDoc> published = kbDocMapper.selectList(new QueryWrapper<KbDoc>()
                .eq("status", KbDocStatus.PUBLISHED.name()));
        int succeeded = 0;
        List<Long> failed = new ArrayList<>();
        for (KbDoc doc : published) {
            if (ragNotifyClient.sendDocSync(doc)) {
                succeeded++;
            } else {
                failed.add(doc.getId());
            }
        }
        return Result.ok(Map.of("total", published.size(), "succeeded", succeeded, "failed", failed));
    }
}
