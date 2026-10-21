package com.viomanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 论坛服务启动类（论坛-独立库与骨架-设计方案 v0.5）。
 * 与 VioManagementApplication 同款：放 com.viomanager 根包，一次组件扫描同时覆盖
 * vio-common 的 com.viomanager.common 与本服务 board/topic/reply/tech/user 各业务包。
 * 鉴权差异：不装配 AuthTokenChecker——JWT 只验签不查库（改密/停用靠 2h 过期兜底）。
 */
@SpringBootApplication
public class VioForumApplication {

    public static void main(String[] args) {
        SpringApplication.run(VioForumApplication.class, args);
    }
}
