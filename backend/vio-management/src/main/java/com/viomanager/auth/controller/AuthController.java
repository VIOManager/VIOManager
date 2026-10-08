package com.viomanager.auth.controller;

import com.viomanager.auth.dto.LoginDTO;
import com.viomanager.auth.dto.PasswordDTO;
import com.viomanager.auth.service.IUserService;
import com.viomanager.common.result.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录、改密。接口口径以骨架接口清单 A01/A02 为准。
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
        // IP 暂取直连地址；X-Forwarded-For 逐跳解析在 Step 6 审计统一做
        return userService.login(loginDTO, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    /**
     * 修改本人密码，改完旧 token 全部失效，前端应引导重新登录。
     */
    @PutMapping("/password")
    public Result changePassword(@Valid @RequestBody PasswordDTO passwordDTO) {
        return userService.changePassword(passwordDTO);
    }
}
