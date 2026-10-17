package com.viomanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 知识库微服务启动类。
 * 与管理系统同款：放 com.viomanager 根包，一次组件扫描覆盖
 * vio-common 的 com.viomanager.common 与本服务 com.viomanager.kb。
 * ⚠️ 本服务不装配 AuthTokenChecker（改密踢人查主库 sys_user，独立库没有该表）——
 * JwtAuthFilter 对该扩展点是可选注入，不装配即跳过（common 设计如此，论坛服务同理）。
 * 安全口径：token 时效 2 小时兜底；与主服务共用 JWT_SECRET，密钥一致则登录态互通。
 */
@SpringBootApplication
public class KbApplication {

    public static void main(String[] args) {
        SpringApplication.run(KbApplication.class, args);
    }
}
