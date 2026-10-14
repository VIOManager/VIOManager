package com.viomanager.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.KbErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.kb.entity.KbCollect;
import com.viomanager.kb.entity.KbDoc;
import com.viomanager.kb.mapper.KbCollectMapper;
import com.viomanager.kb.mapper.KbDocMapper;
import com.viomanager.kb.service.KbCollectService;
import com.viomanager.kb.support.KbDocAccessSupport;
import com.viomanager.kb.support.KbDocQuerySupport;
import com.viomanager.kb.vo.KbCollectVO;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库收藏实现（KB05-07）。
 * 并发对策：幂等键是第一道防线（Controller 注解），uk_kb_collect 唯一键是最终防线——
 * 并发穿透时 DuplicateKeyException 转 9001 业务码，绝不让它变成 500。
 * 计数与收藏行同事务原子 ±1，不会出现"收藏了但计数没加"。
 */
@Service
public class KbCollectServiceImpl implements KbCollectService {

    @Resource
    private KbCollectMapper kbCollectMapper;

    @Resource
    private KbDocMapper kbDocMapper;

    @Resource
    private KbDocAccessSupport accessSupport;

    @Resource
    private KbDocQuerySupport querySupport;

    /** KB05 收藏：文档须 PUBLISHED（9002）；重复收藏 9001 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> collect(Long docId) {
        accessSupport.requirePublished(docId);
        KbCollect collect = new KbCollect();
        collect.setDocId(docId);
        collect.setMemberId(UserHolder.getUser().getUserId());
        try {
            kbCollectMapper.insert(collect);
        } catch (DuplicateKeyException e) {
            throw new BizException(KbErrorCode.KB_ALREADY_COLLECTED);
        }
        kbDocMapper.adjustCollectCount(docId, 1);
        return Result.ok();
    }

    /** KB06 取消收藏：幂等（本来就没收藏也返回成功）；计数只在真删了一行时回退 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> uncollect(Long docId) {
        QueryWrapper<KbCollect> qw = new QueryWrapper<>();
        qw.eq("doc_id", docId).eq("member_id", UserHolder.getUser().getUserId());
        int removed = kbCollectMapper.delete(qw);
        if (removed == 1) {
            //文档已逻辑删时 UPDATE 不命中（SQL 带 deleted=0），计数自然不动，无一致性风险
            kbDocMapper.adjustCollectCount(docId, -1);
        }
        return Result.ok();
    }

    /**
     * KB07 我的收藏：时间倒序带文档摘要。
     * 口径：已下架/已删除的文档"随文档整体不可见"（清单 §5）——列表过滤为 PUBLISHED，
     * total 仍按收藏总数返回（含不可见），联调时如需一致口径再改（方案待确认项）。
     */
    @Override
    public Result<PageResult<KbCollectVO>> myCollections(Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, 100);
        Long memberId = UserHolder.getUser().getUserId();

        QueryWrapper<KbCollect> qw = new QueryWrapper<>();
        qw.eq("member_id", memberId).orderByDesc("created_at");
        Page<KbCollect> result = kbCollectMapper.selectPage(new Page<>(p, s), qw);
        if (result.getRecords().isEmpty()) {
            return Result.ok(PageResult.of(List.of(), result.getTotal(), p, s));
        }

        Map<Long, KbDoc> docById = querySupport
                .selectVisibleDocs(result.getRecords().stream().map(KbCollect::getDocId).toList())
                .stream()
                .collect(Collectors.toMap(KbDoc::getId, Function.identity()));
        List<KbCollectVO> vos = result.getRecords().stream()
                .filter(c -> docById.containsKey(c.getDocId()))
                .map(c -> {
                    KbDoc doc = docById.get(c.getDocId());
                    KbCollectVO vo = new KbCollectVO();
                    vo.setDocId(doc.getId());
                    vo.setTitle(doc.getTitle());
                    vo.setCategory(doc.getCategory());
                    vo.setSummary(doc.getSummary());
                    vo.setSourceType(doc.getSourceType());
                    vo.setCollectedAt(c.getCreatedAt());
                    return vo;
                })
                .toList();
        return Result.ok(PageResult.of(vos, result.getTotal(), p, s));
    }
}
