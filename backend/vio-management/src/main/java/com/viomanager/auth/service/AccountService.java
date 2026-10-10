package com.viomanager.auth.service;

/**
 * 账号管理进程内契约（K4 结论：骨架不出 HTTP 面，A 线 M13 的接口在内部调这里）。
 * A 线用法：@Resource 注入后直接调方法，登录名的默认规则与初始密码都在这里兜住。
 */
public interface AccountService {

    /**
     * 给人员档案绑定登录账号：初始密码 abc123、defaultPassword=1（首次登录弹改密提示）。
     * 登录名已存在返回错误码 2003。
     *
     * @return 新账号 ID
     */
    Long bindAccount(Long memberId, String loginName, String role, Long orgId, String realName);

    /**
     * 修改登录名，新登录名已存在返回错误码 2003。
     */
    void changeLoginName(Long userId, String newLoginName);

    /**
     * 重置密码：回到初始密码 abc123，defaultPassword 回 1，旧 token 全部失效。
     */
    void resetPassword(Long userId);
}
