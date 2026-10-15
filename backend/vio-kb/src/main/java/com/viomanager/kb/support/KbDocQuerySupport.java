package com.viomanager.kb.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.KbDocStatus;
import com.viomanager.common.result.PageResult;
import com.viomanager.kb.dto.KbAdminQueryDTO;
import com.viomanager.kb.dto.KbDocQueryDTO;
import com.viomanager.kb.entity.KbCollect;
import com.viomanager.kb.entity.KbDoc;
import com.viomanager.kb.mapper.KbCollectMapper;
import com.viomanager.kb.mapper.KbDocMapper;
import com.viomanager.kb.vo.KbDocDetailVO;
import com.viomanager.kb.vo.KbDocVO;
import com.viomanager.kb.vo.KbPromptVO;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 知识库读口（KB01-04 / KB17），对齐成果域"写口在 ServiceImpl、读口在 QuerySupport"的分法。
 * 全部列表查询显式 SELECT 列（排除 content——LONGTEXT 大字段不进列表，方案对策）。
 */
@Component
public class KbDocQuerySupport {

    /** 列表/卡片公共列（⚠️ 不含 content；拍板砍掉的字段（category 等）不选不返） */
    public static final String[] LIST_COLUMNS = {
            "id", "title", "summary", "source_type", "source_url", "attach_file_id",
            "file_name", "publisher_name", "top_flag", "view_count", "collect_count",
            "comment_count", "created_at"};

    @Resource
    private KbDocMapper kbDocMapper;

    @Resource
    private KbCollectMapper kbCollectMapper;

    @Resource
    private KbDocAccessSupport accessSupport;

    @Resource
    private KbRecommendCache recommendCache;

    /** KB01 浏览列表：仅 PUBLISHED；置顶优先再时间倒序；keyword 走 FULLTEXT ngram */
    public List<KbDocVO> list(KbDocQueryDTO dto) {
        QueryWrapper<KbDoc> qw = new QueryWrapper<>();
        qw.select(LIST_COLUMNS)
                .eq("status", KbDocStatus.PUBLISHED.name())
                .eq(StringUtils.hasText(dto.getCategory()), "category", dto.getCategory())
                .eq(StringUtils.hasText(dto.getSourceType()), "source_type", dto.getSourceType())
                .apply(StringUtils.hasText(dto.getKeyword()),
                        "MATCH(title, summary) AGAINST({0} IN BOOLEAN MODE)", dto.getKeyword())
                .orderByDesc("top_flag", "created_at");
        return kbDocMapper.selectList(qw).stream().map(this::toVO).toList();
    }

    /** KB02 详情：非 PUBLISHED 在门槛处 9002；浏览数原子 +1；favorited 强一致现查 */
    public KbDocDetailVO detail(Long id) {
        KbDoc doc = accessSupport.requirePublished(id);
        // 原子自增（同步直更库；100-200 人规模足够，阶段 3 再议 Redis 计数——清单 §6 口径）
        kbDocMapper.increaseViewCount(id);

        KbDocDetailVO vo = new KbDocDetailVO();
        fillCard(vo, doc);
        vo.setViewCount(doc.getViewCount() + 1);
        vo.setFavorited(isCollected(id, UserHolder.getUser().getUserId()));
        return vo;
    }

    /** KB03 推荐位：置顶优先再时间倒序取前 N（默认 5 上限 10），Redis 60s 缓存（按条数分键） */
    public List<KbDocVO> recommend(Integer limit) {
        int n = (limit == null || limit < 1) ? 5 : Math.min(limit, 10);
        return recommendCache.get(n, () -> {
            QueryWrapper<KbDoc> qw = new QueryWrapper<>();
            qw.select(LIST_COLUMNS)
                    .eq("status", KbDocStatus.PUBLISHED.name())
                    .orderByDesc("top_flag", "created_at")
                    .last("LIMIT " + n);
            return kbDocMapper.selectList(qw).stream().map(this::toVO).toList();
        });
    }

    /** KB04 AI 提示词列表：is_prompt=1 且 PUBLISHED */
    public List<KbPromptVO> prompts() {
        QueryWrapper<KbDoc> qw = new QueryWrapper<>();
        qw.select("id", "title", "summary", "prompt_schema", "category")
                .eq("is_prompt", 1)
                .eq("status", KbDocStatus.PUBLISHED.name())
                .orderByDesc("top_flag", "created_at");
        return kbDocMapper.selectList(qw).stream().map(doc -> {
            KbPromptVO vo = new KbPromptVO();
            vo.setId(doc.getId());
            vo.setTitle(doc.getTitle());
            vo.setSummary(doc.getSummary());
            vo.setPromptSchema(doc.getPromptSchema());
            vo.setCategory(doc.getCategory());
            return vo;
        }).toList();
    }

    /**
     * KB17 管理视角列表（含 DRAFT/OFFLINE）。
     * 数据范围 v1：超管全量；部门管理员=本组织精确匹配（JWT orgId）——
     * "本子树"与"org_id 为空的全局文档仅超管可管"是清单待确认项，待 org 能力可跨服务获取后升级
     * （独立库无 sys_org，不能走拦截器 ORG 注入，见 KbDocMapper 注释）。
     */
    public PageResult<KbDocVO> adminList(KbAdminQueryDTO dto) {
        CurrentUser user = UserHolder.getUser();
        int page = dto.getPage() == null || dto.getPage() < 1 ? 1 : dto.getPage();
        int size = dto.getSize() == null || dto.getSize() < 1 ? 20 : Math.min(dto.getSize(), 100);

        QueryWrapper<KbDoc> qw = new QueryWrapper<>();
        qw.select(LIST_COLUMNS)
                .eq(StringUtils.hasText(dto.getStatus()), "status", dto.getStatus())
                .eq(StringUtils.hasText(dto.getCategory()), "category", dto.getCategory())
                .apply(StringUtils.hasText(dto.getKeyword()),
                        "MATCH(title, summary) AGAINST({0} IN BOOLEAN MODE)", dto.getKeyword())
                .orderByDesc("updated_at");
        if (!"SUPER_ADMIN".equals(user.getRole())) {
            // 部门管理员（学生已被 Controller 角色注解挡下）；orgId 为空时查不到任何行，从严处理
            qw.eq("org_id", user.getOrgId());
        }

        Page<KbDoc> result = kbDocMapper.selectPage(new Page<>(page, size), qw);
        List<KbDocVO> vos = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(vos, result.getTotal(), page, size);
    }

    /** 批量取"当前仍 PUBLISHED"的文档（KB07 我的收藏过滤用：下架/删除的随文档整体不可见） */
    public List<KbDoc> selectVisibleDocs(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        QueryWrapper<KbDoc> qw = new QueryWrapper<>();
        qw.select(LIST_COLUMNS)
                .in("id", ids)
                .eq("status", KbDocStatus.PUBLISHED.name());
        return kbDocMapper.selectList(qw);
    }

    /** 当前用户是否已收藏（强一致个性化字段，永不缓存） */
    public boolean isCollected(Long docId, Long memberId) {
        if (memberId == null) {
            return false;
        }
        QueryWrapper<KbCollect> qw = new QueryWrapper<>();
        qw.eq("doc_id", docId).eq("member_id", memberId);
        return kbCollectMapper.selectCount(qw) > 0;
    }

    /** 实体 → 列表卡片 VO（KB12 创建成功返回复用，public 供 ServiceImpl 调用） */
    public KbDocVO toVO(KbDoc doc) {
        KbDocVO vo = new KbDocVO();
        fillCard(vo, doc);
        return vo;
    }

    /** 卡片公共字段填充（KbDocDetailVO 继承 KbDocVO，列表/详情共用） */
    private void fillCard(KbDocVO vo, KbDoc doc) {
        vo.setId(doc.getId());
        vo.setTitle(doc.getTitle());
        vo.setSummary(doc.getSummary());
        vo.setSourceType(doc.getSourceType());
        vo.setLinkUrl(doc.getSourceUrl());
        vo.setFileId(doc.getAttachFileId());
        vo.setFileName(doc.getFileName());
        vo.setPinned(doc.getTopFlag() != null && doc.getTopFlag() == 1);
        vo.setViewCount(doc.getViewCount());
        vo.setFavoriteCount(doc.getCollectCount());
        vo.setCommentCount(doc.getCommentCount());
        vo.setPublisherName(doc.getPublisherName());
        vo.setCreatedAt(doc.getCreatedAt());
    }
}
