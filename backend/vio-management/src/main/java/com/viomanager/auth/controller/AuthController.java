package com.viomanager.auth.controller;

import com.viomanager.auth.dto.LoginDTO;
import com.viomanager.auth.dto.PasswordDTO;
import com.viomanager.auth.service.IUserService;
import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.result.Result;
import com.viomanager.common.util.IpUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录、改密、本人档案。接口口径以骨架接口清单 A01/A02 为准。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private IUserService userService;

    /**
     * 登录：成功返回 token 与身份信息，前端据此路由到对应端首页。
     */
    @PostMapping("/login")
    public Result login(@Valid @RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        // IP 逐跳解析 X-Forwarded-For 取真实客户端地址（Step 3 遗留项在此落地）
        return userService.login(loginDTO, IpUtil.resolveClientIp(request), request.getHeader("User-Agent"));
    }

    /**
     * 修改本人密码，改完旧 token 全部失效，前端应引导重新登录。
     * 敏感接口：参数（新旧密码）不落审计，只记"谁改了密码"。
     */
    @AuditLog(bizType = "AUTH_PASSWORD_CHANGE", label = "修改密码", summary = "修改本人登录密码", sensitive = true)
    @PutMapping("/password")
    public Result changePassword(@Valid @RequestBody PasswordDTO passwordDTO) {
        return userService.changePassword(passwordDTO);
    }

    /**
     * 本人档案（论坛独立库方案 §二.3）：登录态，orgName 由 orgId 查 sys_org。
     * 论坛服务在 forum_user 快照首建/组织变化时转发用户 JWT 调用此接口换组织名。
     */
    @GetMapping("/my-profile")
    public Result myProfile() {
        return userService.myProfile();
    }
}
