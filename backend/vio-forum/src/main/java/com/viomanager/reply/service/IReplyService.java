package com.viomanager.reply.service;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.result.PageResult;
import com.viomanager.reply.dto.ReplyCreateDTO;
import com.viomanager.reply.vo.ReplyItemVO;

/**
 * 楼层服务（F05/F07/F10）。
 */
public interface IReplyService {

    /**
     * F05 楼层列表：按楼层号升序分页；HIDDEN 楼层占位返回（内容置"该楼层已被隐藏"）。
     */
    PageResult<ReplyItemVO> list(Long topicId, long page, long size);

    /**
     * F07 回帖：锁定禁回（9101）→ 楼层取号（MAX+1，撞 uk 重试一次）→ 主题计数与最后活跃刷新。
     */
    java.util.Map<String, Object> create(Long topicId, ReplyCreateDTO dto, CurrentUser user, String authHeader);

    /**
     * F10 楼层点赞/取消（状态型天然幂等）。
     */
    java.util.Map<String, Object> like(Long replyId, boolean liked, CurrentUser user);
}
