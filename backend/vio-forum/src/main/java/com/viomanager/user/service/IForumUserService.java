package com.viomanager.user.service;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.user.entity.ForumUser;

/**
 * 论坛用户快照服务。
 */
public interface IForumUserService {

    /**
     * 写操作前调用：快照不存在则建（姓名从 JWT 冻结，组织名经管理侧 my-profile 换取）；
     * 已存在则刷新 last_active_at，且 JWT orgId 与快照不一致时（用户调动）重新换名。
     * 管理侧不可达时组织名按空降级，不阻塞发帖。
     *
     * @param user       当前登录人（ThreadLocal 身份）
     * @param authHeader 原样转发给管理侧 my-profile 的 Authorization 头（Bearer JWT）
     * @return 最新快照（author_name / author_group 从这里取）
     */
    ForumUser touch(CurrentUser user, String authHeader);

    /**
     * F03 社区运行状况用：论坛写过东西的账号数。
     */
    long countMembers();
}
