package com.viomanager.reply.service;

import com.viomanager.reply.dto.ReplyStatusDTO;
import com.viomanager.reply.dto.ReplyUpdateDTO;
import java.util.Map;

/**
 * F13/F14 管理档评论操作（2026-10-17 拍板：论坛权限仅学生/管理两档，管理档=超管+部门管理员）。
 * 删除=HIDDEN 软删占位、恢复=NORMAL，不做物理删除。
 */
public interface IReplyAdminService {

    /** F13 编辑评论：覆盖原文并置 edited=1 */
    Map<String, Object> update(Long replyId, ReplyUpdateDTO dto);

    /** F14 状态设置：to ∈ {NORMAL, HIDDEN} */
    Map<String, Object> setStatus(Long replyId, ReplyStatusDTO dto);
}
