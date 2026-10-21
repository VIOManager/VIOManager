package com.viomanager.topic.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.board.entity.ForumBoard;
import com.viomanager.board.service.IBoardService;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.enums.ForumErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.topic.dto.TopicCreateDTO;
import com.viomanager.topic.entity.ForumTopic;
import com.viomanager.topic.entity.ForumTopicCollect;
import com.viomanager.topic.entity.ForumTopicLike;
import com.viomanager.topic.mapper.ForumTopicCollectMapper;
import com.viomanager.topic.mapper.ForumTopicLikeMapper;
import com.viomanager.topic.mapper.ForumTopicMapper;
import com.viomanager.topic.service.ITopicCommandService;
import com.viomanager.support.TagCodec;
import com.viomanager.user.entity.ForumUser;
import com.viomanager.user.service.IForumUserService;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 主题命令服务实现（F06/F08/F09）。
 * 计数与点赞/收藏行在同一事务里维护，防止计数与事实表漂移。
 */
@Service
public class TopicCommandServiceImpl implements ITopicCommandService {

    @Resource
    private ForumTopicMapper forumTopicMapper;
    @Resource
    private ForumTopicLikeMapper forumTopicLikeMapper;
    @Resource
    private ForumTopicCollectMapper forumTopicCollectMapper;
    @Resource
    private IBoardService boardService;
    @Resource
    private IForumUserService forumUserService;

    @Override
    public Map<String, Object> create(TopicCreateDTO dto, CurrentUser user, String authHeader) {
        //1.版块守卫：不存在或停用 → 9104
        ForumBoard board = boardService.findByCode(dto.getBoard());
        if (board == null) {
            throw new BizException(ForumErrorCode.BOARD_DISABLED);
        }
        //2.快照冻结（首见建档 / 调动换名，管理侧不可达时组织名降级为空）
        ForumUser snapshot = forumUserService.touch(user, authHeader);
        //3.建主题：置顶仅超管生效，其他角色静默置 0（前端勾选框本就只对管理员可见）
        boolean pinned = Boolean.TRUE.equals(dto.getPinned()) && "SUPER_ADMIN".equals(user.getRole());
        ForumTopic topic = new ForumTopic()
                .setBoardId(board.getId())
                .setTitle(dto.getTitle())
                .setBody(dto.getBodyText())
                .setTags(TagCodec.join(dto.getTags()))
                .setAuthorUserId(user.getUserId())
                .setAuthorName(snapshot.getRealName())
                .setAuthorGroup(snapshot.getOrgName())
                .setStatus("NORMAL")
                .setPinned(pinned ? 1 : 0)
                .setReplyCount(0)
                .setViewCount(0)
                .setLikeCount(0)
                .setCollectCount(0)
                .setLastReplyAt(LocalDateTime.now());
        forumTopicMapper.insert(topic);
        return Map.of("id", topic.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> like(Long topicId, boolean liked, CurrentUser user) {
        ForumTopic topic = requireVisible(topicId);
        //changed 标记"本次是否真动了库"：重复点赞（uk 撞键）/重复取消（无行可删）都不动计数，
        //返回的 likeCount 按事实算，不能无脑 ±1（虚报会让调用方显示漂移）
        boolean changed = false;
        if (liked) {
            try {
                forumTopicLikeMapper.insert(new ForumTopicLike()
                        .setTopicId(topicId).setUserId(user.getUserId()));
                forumTopicMapper.update(null, new UpdateWrapper<ForumTopic>()
                        .eq("id", topicId).setSql("like_count = like_count + 1"));
                changed = true;
            } catch (DuplicateKeyException e) {
                //uk 撞键 = 已赞过，幂等不重复计数
            }
        } else {
            int removed = forumTopicLikeMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ForumTopicLike>()
                        .eq("topic_id", topicId).eq("user_id", user.getUserId()));
            if (removed > 0) {
                forumTopicMapper.update(null, new UpdateWrapper<ForumTopic>()
                        .eq("id", topicId).setSql("like_count = GREATEST(like_count - 1, 0)"));
                changed = true;
            }
        }
        long likeCount = changed
                ? (liked ? topic.getLikeCount() + 1 : Math.max(topic.getLikeCount() - 1, 0))
                : topic.getLikeCount();
        return Map.of("liked", liked, "likeCount", likeCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> collect(Long topicId, boolean collected, CurrentUser user) {
        ForumTopic topic = requireVisible(topicId);
        if (collected) {
            try {
                forumTopicCollectMapper.insert(new ForumTopicCollect()
                        .setTopicId(topicId).setUserId(user.getUserId()));
                forumTopicMapper.update(null, new UpdateWrapper<ForumTopic>()
                        .eq("id", topicId).setSql("collect_count = collect_count + 1"));
            } catch (DuplicateKeyException e) {
                //uk 撞键 = 已收藏，幂等不重复计数
            }
        } else {
            int removed = forumTopicCollectMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ForumTopicCollect>()
                        .eq("topic_id", topicId).eq("user_id", user.getUserId()));
            if (removed > 0) {
                forumTopicMapper.update(null, new UpdateWrapper<ForumTopic>()
                        .eq("id", topicId).setSql("collect_count = GREATEST(collect_count - 1, 0)"));
            }
        }
        return Map.of("collected", collected);
    }

    /** 可见性守卫：不存在/已删/隐藏一律 9102 */
    private ForumTopic requireVisible(Long topicId) {
        ForumTopic topic = forumTopicMapper.selectById(topicId);
        if (topic == null || "HIDDEN".equals(topic.getStatus())) {
            throw new BizException(ForumErrorCode.TOPIC_NOT_FOUND);
        }
        return topic;
    }
}
