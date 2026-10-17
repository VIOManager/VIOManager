package com.viomanager.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器：验签 → 附加校验（可选）→ 身份入 ThreadLocal。
 * 失败统一返回 HTTP 401 + code=1002。
 * ⚠️ 冲突：401 和 1002 的配对是前端拦截器跳登录页的契约（规范 §5.1），改了前端全部失效。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 附加校验扩展点：管理系统装配查库校验（停用/改密踢人）；
     * 阶段2 论坛不装配，字段为 null 自然跳过——common 不依赖 management 的库。
     * 注：可选注入只能用 @Autowired(required=false)，@Resource 没有 required 属性
     */
    @Autowired(required = false)
    private AuthTokenChecker authTokenChecker;

    /** 免认证路径，逗号分隔，在 application.yml 的 vio.auth.skip-paths 配置 */
    @Value("${vio.auth.skip-paths:/api/health,/api/auth/login}")
    private String skipPaths;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        //跨域预检请求不带 Authorization（浏览器契约），放行给 Spring 的 CORS 处理链，
        //否则受保护接口的预检先死在 401，前端联调全挂
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        String uri = request.getRequestURI();
        String[] paths = skipPaths.split(",");
        for (String p : paths) {
            if (uri.equals(p.trim())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            //1.取 Authorization 头，必须是 Bearer 开头
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                write401(response);
                return;
            }
            //2.验签解析载荷，失败按未登录处理
            String token = authHeader.substring(7);
            CurrentUser user;
            try {
                user = jwtUtil.parseToken(token);
            } catch (BizException e) {
                write401(response);
                return;
            }
            //3.管理系统侧附加校验（停用 / 改密踢人），没装配就跳过
            if (authTokenChecker != null) {
                try {
                    authTokenChecker.check(user);
                } catch (BizException e) {
                    write401(response);
                    return;
                }
            }
            //4.维护锁定检查：学期切换执行期全员禁访（PRD §3.4），超管放行观察；
            //   刻意 503 + 1012，不碰 401/1002 的"跳登录"契约
            if (MaintenanceLock.isLocked() && !"SUPER_ADMIN".equals(user.getRole())) {
                response.setStatus(503);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(
                        Result.fail(CommonErrorCode.SYSTEM_MAINTENANCE)));
                return;
            }
            //5.身份入 ThreadLocal，放行
            UserHolder.saveUser(user);
            filterChain.doFilter(request, response);
        } finally {
            // ⚠️ 安全：Tomcat 线程池复用线程，不清理的话下一个请求会拿到上一个用户的身份——严重串号事故
            UserHolder.removeUser();
        }
    }

    /** 按 401 + code=1002 的契约写出失败响应 */
    private void write401(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(CommonErrorCode.UNAUTHORIZED)));
    }
}
