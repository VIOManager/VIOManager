package com.viomanager.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.KbCommentStatus;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.kb.dto.KbCommentCreateDTO;
import com.viomanager.kb.entity.KbComment;
import com.viomanager.kb.mapper.KbCommentMapper;
import com.viomanager.kb.mapper.KbDocMapper;
import com.viomanager.kb.service.KbCommentService;
import com.viomanager.kb.support.KbDocAccessSupport;
import com.viomanager.kb.vo.KbCommentVO;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库评论实现（KB08-11）。
 * 楼中楼口径：顶层分页（时间正序）+ 一次 IN 查子回复内存组装；父级 HIDDEN/删除整楼不返回（查询侧折叠）。
 * 计数口径=可见数：发 +1；删（仅 VISIBLE 状态）-1——隐藏的本来就没计数，删除不重复减；
 * 隐藏 -1 / 恢复 +1，全部与状态变更同事务。
 */
@Service
public class KbCommentServiceImpl implements KbCommentService {

    @Resource
    private KbCommentMapper kbCommentMapper;

    @Resource
    private KbDocMapper kbDocMapper;

    @Resource
    private KbDocAccessSupport accessSupport;

    /** KB08 评论列表（时间正序，含楼中楼） */
    @Override
    public Result<PageResult<KbCommentVO>> list(Long docId, Integer page, Integer size) {
        accessSupport.requirePublished(docId);
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, 100);
        Long me = UserHolder.getUser().getUserId();

        //1.顶层评论分页（时间正序，仅可见）
        QueryWrapper<KbComment> topQw = new QueryWrapper<>();
        topQw.eq("doc_id", docId)
                .isNull("parent_id")
                .eq("status", KbCommentStatus.VISIBLE.name())
                .orderByAsc("created_at");
        Page<KbComment> top = kbCommentMapper.selectPage(new Page<>(p, s), topQw);
        if (top.getRecords().isEmpty()) {
            return Result.ok(PageResult.of(List.of(), top.getTotal(), p, s));
        }

        //2.本页顶层的子回复一次 IN 查回（避免 N+1，规范性能要求），时间正序
        List<Long> topIds = top.getRecords().stream().map(KbComment::getId).toList();
        QueryWrapper<KbComment> replyQw = new QueryWrapper<>();
        replyQw.in("parent_id", topIds)
                .eq("status", KbCommentStatus.VISIBLE.name())
                .orderByAsc("created_at");
        Map<Long, List<KbComment>> repliesByParent = kbCommentMapper.selectList(replyQw).stream()
                .collect(Collectors.groupingBy(KbComment::getParentId));

        //3.组装楼中楼（父级不可见的子回复随分组自然不出现）
        List<KbCommentVO> vos = top.getRecords().stream()
                .map(c -> toVO(c, me, repliesByParent.getOrDefault(c.getId(), List.of()), me))
                .toList();
        return Result.ok(PageResult.of(vos, top.getTotal(), p, s));
    }

    /** KB09 发表/回复：父评论须存在且同文档（同文案拒绝，不给探测空间） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> create(Long docId, KbCommentCreateDTO dto) {
        accessSupport.requirePublished(docId);
        if (dto.getParentId() != null) {
            KbComment parent = kbCommentMapper.selectById(dto.getParentId());
            if (parent == null || !parent.getDocId().equals(docId)) {
                throw new BizException(CommonErrorCode.PARAM_INVALID, "父评论不存在或不属于该文档");
            }
        }
        CurrentUser user = UserHolder.getUser();
        KbComment comment = new KbComment();
        comment.setDocId(docId);
        comment.setParentId(dto.getParentId());
        comment.setContent(dto.getContent());
        //memberId/memberName 口径：JWT 身份快照（独立服务无 member 表，方案待确认项 1），改名不回溯
        comment.setMemberId(user.getUserId());
        comment.setMemberName(user.getRealName());
        comment.setStatus(KbCommentStatus.VISIBLE.name());
        kbCommentMapper.insert(comment);
        kbDocMapper.adjustCommentCount(docId, 1);
        return Result.ok(comment.getId());
    }

    /** KB10 删除：本人或管理员；仅 VISIBLE 状态的删除才回退计数 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(Long commentId) {
        KbComment comment = kbCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "评论不存在");
        }
        CurrentUser user = UserHolder.getUser();
        boolean admin = "SUPER_ADMIN".equals(user.getRole()) || "DEPT_ADMIN".equals(user.getRole());
        if (!admin && !comment.getMemberId().equals(user.getUserId())) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "仅本人或管理员可删除评论");
        }
        kbCommentMapper.deleteById(commentId);
        if (KbCommentStatus.VISIBLE.name().equals(comment.getStatus())) {
            //隐藏状态的评论未计入 comment_count，删除不重复减
            kbDocMapper.adjustCommentCount(comment.getDocId(), -1);
        }
        return Result.ok();
    }

    /** KB11 隐藏/恢复（管理员）：条件更新防并发双击，计数随可见口径 ±1 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> hide(Long commentId, boolean hidden) {
        KbComment comment = kbCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "评论不存在");
        }
        String target = hidden ? KbCommentStatus.HIDDEN.name() : KbCommentStatus.VISIBLE.name();
        if (target.equals(comment.getStatus())) {
            return Result.ok();
        }
        UpdateWrapper<KbComment> uw = new UpdateWrapper<>();
        uw.eq("id", commentId)
                .eq("status", comment.getStatus())
                .set("status", target)
                .set("updated_at", LocalDateTime.now());
        int rows = kbCommentMapper.update(null, uw);
        if (rows == 0) {
            //并发下已被处理（可能恰好被别人设成同目标），幂等成功
            return Result.ok();
        }
        kbDocMapper.adjustCommentCount(comment.getDocId(), hidden ? -1 : 1);
        return Result.ok();
    }

    /** 评论实体 → VO（含子回复递归一层；本模块楼中楼只有两层） */
    private KbCommentVO toVO(KbComment c, Long me, List<KbComment> replies, Long currentUserId) {
        KbCommentVO vo = new KbCommentVO();
        vo.setId(c.getId());
        vo.setMemberId(c.getMemberId());
        vo.setMemberName(c.getMemberName());
        vo.setContent(c.getContent());
        vo.setCreatedAt(c.getCreatedAt());
        vo.setMine(c.getMemberId() != null && c.getMemberId().equals(currentUserId));
        vo.setChildren(replies.stream()
                .map(r -> toVO(r, null, List.of(), currentUserId))
                .toList());
        return vo;
    }
}
