package com.viomanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置。阶段 1 前后端直连（Vite dev 5174 → 8088），后端必须显式放行——
 * Spring 6 在无任何 CORS 配置时对预检请求一律 403（DefaultCorsProcessor 默认拒绝），
 * 前端联调曾全挂在这。阶段 2 Nginx 同源转发后无需跨域，origins 留空即不注册映射。
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    /** 逗号分隔的来源白名单，空值不注册任何跨域映射（生产同源部署即此形态） */
    @Value("${vio.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.isBlank()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("*")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
