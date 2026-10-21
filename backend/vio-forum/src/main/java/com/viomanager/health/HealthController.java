package com.viomanager.health;

import com.viomanager.common.result.Result;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 论坛服务健康检查：部署探活与本地验证用，无鉴权、无业务逻辑。
 * 口径与管理侧 H01 一致（该接口不录 Apifox）。
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
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("version", version);
        return Result.ok(data);
    }
}
