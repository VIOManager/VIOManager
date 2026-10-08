package com.viomanager.health;

import com.viomanager.common.result.Result;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查：部署探活与本地环境验证用，无鉴权、无业务逻辑。
 * 接口口径以骨架接口清单 H01 为准（该接口不录 Apifox）。
 */
@RestController
public class HealthController {

    /** 服务版本号，构建时由 Maven 资源过滤从 project.version 注入。 */
    @Value("${app.version}")
    private String version;

    /**
     * 报告服务存活状态与版本号。
     */
    @GetMapping("/api/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of("status", "UP", "version", version));
    }
}
