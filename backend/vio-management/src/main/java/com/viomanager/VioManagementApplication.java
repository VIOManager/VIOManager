package com.viomanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 管理系统服务启动类。
 * 放在 com.viomanager 根包，让一次组件扫描同时覆盖 vio-common 的
 * com.viomanager.common 与本服务各业务模块包（骨架方案 §2.1 约定）。
 * @EnableScheduling：Step 9 审批过期定时任务需要。
 */
@SpringBootApplication
@EnableScheduling
public class VioManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(VioManagementApplication.class, args);
    }
}
