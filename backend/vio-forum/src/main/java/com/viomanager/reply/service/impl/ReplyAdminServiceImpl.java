package com.viomanager.reply.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.ForumErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.reply.dto.ReplyStatusDTO;
import com.viomanager.reply.dto.ReplyUpdateDTO;
import com.viomanager.reply.entity.ForumReply;
import com.viomanager.reply.mapper.ForumReplyMapper;
import com.viomanager.reply.service.IReplyAdminService;
import jakarta.annotation.Resource;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理档评论操作实现（F13 编辑 / F14 状态）。角色门在 Controller @RequireRole，服务层只做业务守卫。
 */
@Service
public class ReplyAdminServiceImpl implements IReplyAdminService {

    @Resource
    private ForumReplyMapper forumReplyMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(Long replyId, ReplyUpdateDTO dto) {
        ForumReply reply = requireReply(replyId);
        forumReplyMapper.update(null, new UpdateWrapper<ForumReply>()
                .eq("id", replyId)
                .set("content", dto.getText())
                .set("edited", 1));
        return Map.of("id", replyId, "edited", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> setStatus(Long replyId, ReplyStatusDTO dto) {
        requireReply(replyId);
        String to = dto.getTo();
        if (!"NORMAL".equals(to) && !"HIDDEN".equals(to)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "to 取值不合法：仅 NORMAL / HIDDEN");
        }
        forumReplyMapper.update(null, new UpdateWrapper<ForumReply>()
                .eq("id", replyId)
                .set("status", to));
        return Map.of("id", replyId, "status", to);
    }

    /** 9103 守卫：不存在或已删（HIDDEN 可被管理员编辑/恢复，是操作对象不是禁区） */
    private ForumReply requireReply(Long replyId) {
        ForumReply reply = forumReplyMapper.selectById(replyId);
        if (reply == null) {
            throw new BizException(ForumErrorCode.REPLY_NOT_FOUND);
        }
        return reply;
    }
}
