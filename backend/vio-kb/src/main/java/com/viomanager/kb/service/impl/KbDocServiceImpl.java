package com.viomanager.kb.service.impl;

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
import com.viomanager.kb.vo.KbDocDetailVO;
import com.viomanager.kb.vo.KbDocVO;
import com.viomanager.kb.vo.KbPromptVO;
import jakarta.annotation.Resource;
import java.util.List;
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
    public Result<PageResult<KbDocVO>> adminList(KbAdminQueryDTO dto) {
        return Result.ok(querySupport.adminList(dto));
    }

    // ==================== 写口 ====================

    /** KB12 发布/存草稿。条件校验按清单 §5：LINK 必填 sourceUrl；isPrompt=1 必填 promptSchema */
    @Override
    public Result<KbDocVO> create(KbDocCreateDTO dto) {
        //1.条件校验
        String sourceType = StringUtils.hasText(dto.getSourceType())
                ? dto.getSourceType() : KbSourceType.MANUAL.name();
        if (KbSourceType.LINK.name().equals(sourceType) && !StringUtils.hasText(dto.getSourceUrl())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "LINK 类型必填 sourceUrl");
        }
        int isPrompt = dto.getIsPrompt() != null && dto.getIsPrompt() == 1 ? 1 : 0;
        if (isPrompt == 1 && !StringUtils.hasText(dto.getPromptSchema())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "AI 提示词必填 promptSchema");
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
        doc.setCategory(dto.getCategory());
        doc.setIsPrompt(isPrompt);
        doc.setPromptSchema(dto.getPromptSchema());
        doc.setContent(dto.getContent());
        doc.setSummary(dto.getSummary());
        doc.setCoverFileId(dto.getCoverFileId());
        doc.setAttachFileId(dto.getAttachFileId());
        doc.setSourceType(sourceType);
        doc.setSourceUrl(dto.getSourceUrl());
        doc.setTags(dto.getTags());
        doc.setStatus(status);
        doc.setTopFlag(0);
        doc.setViewCount(0);
        doc.setCollectCount(0);
        doc.setCommentCount(0);
        doc.setAiModel(dto.getAiModel());
        // 归属组织缺省取当前登录人组织（管理端归属标记，浏览侧不按此过滤）
        doc.setOrgId(dto.getOrgId() != null ? dto.getOrgId() : user.getOrgId());
        // created_by 基座暂不自动填充（同 FileServiceImpl 口径，手工置）
        doc.setCreatedBy(user.getUserId());
        doc.setUpdatedBy(user.getUserId());
        kbDocMapper.insert(doc);
        //3.推荐位失效（新发布/新草稿都可能改变推荐集）
        recommendCache.invalidate();
        return Result.ok(querySupport.toVO(doc));
    }

    /** KB13 编辑全字段。带 updatedAt 即乐观锁：0 行命中 1007（规范预留码） */
    @Override
    public Result<Void> update(Long id, KbDocUpdateDTO dto) {
        KbDoc existing = accessSupport.requireExists(id);
        //LINK 收紧校验：显式改 LINK 且未给链接、原文档也没有链接 → 拒绝
        if (KbSourceType.LINK.name().equals(dto.getSourceType())
                && !StringUtils.hasText(dto.getSourceUrl())
                && !StringUtils.hasText(existing.getSourceUrl())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "LINK 类型必填 sourceUrl");
        }
        //条件更新：WHERE 带旧 updated_at（若提供），SET 只改传入字段
        UpdateWrapper<KbDoc> uw = new UpdateWrapper<>();
        uw.eq("id", id);
        if (dto.getUpdatedAt() != null) {
            uw.eq("updated_at", dto.getUpdatedAt());
        }
        uw.set(dto.getTitle() != null, "title", dto.getTitle())
                .set(dto.getCategory() != null, "category", dto.getCategory())
                .set(dto.getIsPrompt() != null, "is_prompt", dto.getIsPrompt())
                .set(dto.getPromptSchema() != null, "prompt_schema", dto.getPromptSchema())
                .set(dto.getContent() != null, "content", dto.getContent())
                .set(dto.getSummary() != null, "summary", dto.getSummary())
                .set(dto.getCoverFileId() != null, "cover_file_id", dto.getCoverFileId())
                .set(dto.getAttachFileId() != null, "attach_file_id", dto.getAttachFileId())
                .set(dto.getSourceType() != null, "source_type", dto.getSourceType())
                .set(dto.getSourceUrl() != null, "source_url", dto.getSourceUrl())
                .set(dto.getTags() != null, "tags", dto.getTags())
                .set(dto.getAiModel() != null, "ai_model", dto.getAiModel())
                .set(dto.getOrgId() != null, "org_id", dto.getOrgId())
                .set("updated_by", UserHolder.getUser().getUserId());
        int rows = kbDocMapper.update(null, uw);
        if (rows == 0) {
            if (dto.getUpdatedAt() != null) {
                throw new BizException(CommonErrorCode.CONCURRENT_MODIFIED);
            }
            throw new BizException(CommonErrorCode.NOT_FOUND, "文档不存在");
        }
        recommendCache.invalidate();
        return Result.ok();
    }

    /** KB14 逻辑删除：评论收藏不级联清（随文档整体不可见，清单 §5） */
    @Override
    public Result<Void> delete(Long id) {
        accessSupport.requireExists(id);
        kbDocMapper.deleteById(id);
        recommendCache.invalidate();
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
        return Result.ok();
    }
}
