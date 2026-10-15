package com.viomanager.kb.health;

import com.viomanager.common.result.Result;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查（对齐骨架 H01 口径，不录 Apifox）：部署探活与本地验证用，
 * 无鉴权、无业务逻辑。网关/compose 探活打这里。
 */
@RestController
public class KbHealthController {

    @Value("${app.version}")
    private String version;

    @GetMapping("/api/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "vio-kb");
        data.put("version", version);
        return Result.ok(data);
    }
}
