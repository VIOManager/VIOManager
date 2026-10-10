package com.viomanager.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 认证相关 Bean 装配。
 */
@Configuration
public class AuthBeanConfig {

    /**
     * BCrypt 密码加密器：全组统一用这一个 Bean，别各自 new——
     * 各自 new 容易出现强度参数不一致，导致一边加密一边校验不过。
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
