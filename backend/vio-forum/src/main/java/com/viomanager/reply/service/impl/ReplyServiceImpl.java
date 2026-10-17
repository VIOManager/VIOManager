package com.viomanager.reply.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.enums.ForumErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.PageResult;
import com.viomanager.reply.dto.ReplyCreateDTO;
import com.viomanager.reply.entity.ForumReply;
import com.viomanager.reply.entity.ForumReplyLike;
import com.viomanager.reply.mapper.ForumReplyLikeMapper;
import com.viomanager.reply.mapper.ForumReplyMapper;
import com.viomanager.reply.service.IReplyService;
import com.viomanager.reply.vo.ReplyItemVO;
import com.viomanager.topic.entity.ForumTopic;
import com.viomanager.topic.mapper.ForumTopicMapper;
import com.viomanager.user.entity.ForumUser;
import com.viomanager.user.service.IForumUserService;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 楼层服务实现（F05/F07/F10）。
 */
@Service
public class ReplyServiceImpl implements IReplyService {

    private static final int MAX_PAGE_SIZE = 100;
    /** 引用摘要截断长度（quote_digest 列宽 200） */
    private static final int QUOTE_DIGEST_MAX = 200;

    @Resource
    private ForumReplyMapper forumReplyMapper;
    @Resource
    private ForumReplyLikeMapper forumReplyLikeMapper;
    @Resource
    private ForumTopicMapper forumTopicMapper;
    @Resource
    private IForumUserService forumUserService;

    @Override
    public PageResult<ReplyItemVO> list(Long topicId, long page, long size) {
        //1.主题可见性守卫：隐藏/不存在的话题楼层也不给看
        requireTopicVisible(topicId);
        long p = Math.max(page, 1);
        long s = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        //V4 两级口径：一级（parent_reply_id IS NULL）分页且占楼层序列；二级挂 children 不进分页
        Page<ForumReply> result = forumReplyMapper.selectPage(
                new Page<>(p, s),
                new QueryWrapper<ForumReply>()
                        .eq("topic_id", topicId)
                        .isNull("parent_reply_id")
                        .orderByAsc("floor_no"));
        //1.当前页一级的二级评论全量取回（时间正序按父分组；单楼二级爆量场景本期不优化）
        Map<Long, List<ForumReply>> childrenMap = new HashMap<>();
        List<Long> parentIds = new ArrayList<>();
        for (ForumReply r : result.getRecords()) {
            parentIds.add(r.getId());
        }
        if (!parentIds.isEmpty()) {
            for (ForumReply c : forumReplyMapper.selectList(
                    new QueryWrapper<ForumReply>()
                            .eq("topic_id", topicId)
                            .in("parent_reply_id", parentIds)
                            .orderByAsc("created_at"))) {
                childrenMap.computeIfAbsent(c.getParentReplyId(), k -> new ArrayList<>()).add(c);
            }
        }
        //2.当前用户点赞态批量查（一级 + 二级合并一次 in 查询，不逐楼查库）
        Long userId = com.viomanager.common.auth.UserHolder.getUser().getUserId();
        boolean adminView = isAdmin(com.viomanager.common.auth.UserHolder.getUser());
        List<Long> replyIds = new ArrayList<>(parentIds);
        for (List<ForumReply> cs : childrenMap.values()) {
            for (ForumReply c : cs) {
                replyIds.add(c.getId());
            }
        }
        Set<Long> likedIds = new HashSet<>();
        if (!replyIds.isEmpty()) {
            List<ForumReplyLike> likes = forumReplyLikeMapper.selectList(
                    new QueryWrapper<ForumReplyLike>()
                            .eq("user_id", userId)
                            .in("reply_id", replyIds));
            for (ForumReplyLike like : likes) {
                likedIds.add(like.getReplyId());
            }
        }
        //3.拼装：HIDDEN 楼层学生占位、管理员可见原文（管理恢复操作需要看到内容）
        List<ReplyItemVO> vos = new ArrayList<>();
        for (ForumReply r : result.getRecords()) {
            ReplyItemVO vo = toVO(r, likedIds.contains(r.getId()), adminView);
            List<ReplyItemVO> children = new ArrayList<>();
            for (ForumReply c : childrenMap.getOrDefault(r.getId(), List.of())) {
                children.add(toVO(c, likedIds.contains(c.getId()), adminView));
            }
            vo.setChildren(children);
            vos.add(vo);
        }
        return PageResult.of(vos, result.getTotal(), p, s);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long topicId, ReplyCreateDTO dto, CurrentUser user, String authHeader) {
        //1.主题行锁（并发修复 M1，2026-10-16 实测复现后修复）：同帖回帖在事务最前排队串行取号。
        //  锁必须先于本事务的第一次一致性读（下方 selectById 建快照）——REPEATABLE READ 下
        //  快照建立后再读 MAX(floor_no) 仍是旧值，重试循环无效（实测 10 并发仅 1-2 成功）。
        //  锁定期间 touch() 首访用户的跨服务换名调用会拉长持锁时间（首访罕见，可接受）
        List<Object> lockedRow = forumTopicMapper.selectObjs(
                new QueryWrapper<ForumTopic>().select("id").eq("id", topicId).last("FOR UPDATE"));
        //2.主题守卫：HIDDEN/不存在（9102），LOCKED 禁回（9101）
        ForumTopic topic = lockedRow.isEmpty() ? null : forumTopicMapper.selectById(topicId);
        if (topic == null || "HIDDEN".equals(topic.getStatus())) {
            throw new BizException(ForumErrorCode.TOPIC_NOT_FOUND);
        }
        if ("LOCKED".equals(topic.getStatus())) {
            throw new BizException(ForumErrorCode.TOPIC_LOCKED);
        }
        //3.快照冻结
        ForumUser snapshot = forumUserService.touch(user, authHeader);
        //4.楼层取号：MAX+1（行锁串行后无竞争）；uk 兜底重试保留作残余防线
        ForumReply reply = new ForumReply()
                .setTopicId(topicId)
                .setContent(dto.getText())
                .setAuthorUserId(user.getUserId())
                .setAuthorName(snapshot.getRealName())
                .setAuthorGroup(snapshot.getOrgName())
                .setLikeCount(0)
                .setStatus("NORMAL");
        fillQuote(reply, topicId, dto.getQuoteReplyId());
        insertWithFloorRetry(reply);
        //5.主题计数与最后活跃刷新（与楼层同事务，防计数漂移）
        forumTopicMapper.update(null, new UpdateWrapper<ForumTopic>()
                .eq("id", topicId)
                .setSql("reply_count = reply_count + 1")
                .set("last_reply_at", LocalDateTime.now())
                .set("last_reply_user_id", user.getUserId())
                .set("last_reply_by_name", snapshot.getRealName()));
        return Map.of("id", reply.getId(), "floorNo", reply.getFloorNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> like(Long replyId, boolean liked, CurrentUser user) {
        ForumReply reply = forumReplyMapper.selectById(replyId);
        if (reply == null || "HIDDEN".equals(reply.getStatus())) {
            throw new BizException(ForumErrorCode.REPLY_NOT_FOUND);
        }
        if (liked) {
            try {
                forumReplyLikeMapper.insert(new ForumReplyLike()
                        .setReplyId(replyId).setUserId(user.getUserId()));
                forumReplyMapper.update(null, new UpdateWrapper<ForumReply>()
                        .eq("id", replyId).setSql("like_count = like_count + 1"));
            } catch (DuplicateKeyException e) {
                //已赞过，幂等不重复计数
            }
        } else {
            int removed = forumReplyLikeMapper.delete(
                    new QueryWrapper<ForumReplyLike>()
                            .eq("reply_id", replyId).eq("user_id", user.getUserId()));
            if (removed > 0) {
                forumReplyMapper.update(null, new UpdateWrapper<ForumReply>()
                        .eq("id", replyId).setSql("like_count = GREATEST(like_count - 1, 0)"));
            }
        }
        return Map.of("liked", liked);
    }

    /** 引用条冻结：被引楼层必须同主题且可见，否则静默降级为无引用（不挡回帖；
     *  lzh 2026-10-16 拍板维持此口径，审查 L11 不改报 9103） */
    private void fillQuote(ForumReply reply, Long topicId, Long quoteReplyId) {
        if (quoteReplyId == null) {
            return;
        }
        ForumReply quoted = forumReplyMapper.selectById(quoteReplyId);
        if (quoted == null || !quoted.getTopicId().equals(topicId)
                || "HIDDEN".equals(quoted.getStatus())) {
            return;
        }
        String digest = quoted.getContent();
        if (digest != null && digest.length() > QUOTE_DIGEST_MAX) {
            digest = digest.substring(0, QUOTE_DIGEST_MAX);
        }
        reply.setQuoteReplyId(quoted.getId())
                .setQuoteFloorNo(quoted.getFloorNo())
                .setQuoteAuthorName(quoted.getAuthorName())
                .setQuoteDigest(digest);
    }

    /** 楼层插入：MAX+1 取号；行锁（事务开头）已串行化取号，uk 重试保留作残余防线 */
    private void insertWithFloorRetry(ForumReply reply) {
        for (int attempt = 0; attempt < 2; attempt++) {
            Integer maxFloor = maxFloorNo(reply.getTopicId());
            reply.setFloorNo((maxFloor == null ? 0 : maxFloor) + 1);
            try {
                forumReplyMapper.insert(reply);
                return;
            } catch (DuplicateKeyException e) {
                //并发取到同楼层号：下一轮重新 MAX+1
            }
        }
        throw new BizException(com.viomanager.common.enums.CommonErrorCode.INTERNAL_ERROR,
                "楼层号竞争冲突，请重试");
    }

    /** SELECT MAX(floor_no)：无楼层返回 null */
    private Integer maxFloorNo(Long topicId) {
        List<Object> max = forumReplyMapper.selectObjs(
                new QueryWrapper<ForumReply>()
                        .select("MAX(floor_no)")
                        .eq("topic_id", topicId));
        return (Integer) (max.isEmpty() || max.get(0) == null ? null : ((Number) max.get(0)).intValue());
    }

    private ReplyItemVO toVO(ForumReply r, boolean liked) {
        ReplyItemVO vo = new ReplyItemVO();
        vo.setId(r.getId());
        vo.setFloorNo(r.getFloorNo());
        vo.setAuthor(r.getAuthorName());
        vo.setAuthorGroup(r.getAuthorGroup());
        vo.setPublishAt(r.getCreatedAt());
        vo.setText("HIDDEN".equals(r.getStatus()) ? "该楼层已被隐藏" : r.getContent());
        vo.setLikeCount(r.getLikeCount());
        vo.setLiked(liked);
        if (r.getQuoteReplyId() != null) {
            ReplyItemVO.QuoteVO quote = new ReplyItemVO.QuoteVO();
            quote.setAuthor(r.getQuoteAuthorName());
            quote.setFloorNo(r.getQuoteFloorNo());
            quote.setText(r.getQuoteDigest());
            vo.setQuote(quote);
        }
        return vo;
    }

    private void requireTopicVisible(Long topicId) {
        ForumTopic topic = forumTopicMapper.selectById(topicId);
        if (topic == null || "HIDDEN".equals(topic.getStatus())) {
            throw new BizException(ForumErrorCode.TOPIC_NOT_FOUND);
        }
    }
}
