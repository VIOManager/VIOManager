package com.viomanager.dev;

import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Data;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Step 5 验证探针：给 @Idempotent 注解一个真实可打的接口。
 * executeCount 是业务真实执行次数——命中幂等拦截的请求到不了方法体，
 * 测试用它断言"同 key 第二次请求没有再次执行业务"。
 * @Profile("dev") 只在本地开发环境注册，生产不暴露（上线检查清单核对项）。
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class IdempotentProbeController {

    /** 业务真实执行次数：只增不减，测试断言增量而不是绝对值 */
    public static final AtomicLong EXECUTE_COUNT = new AtomicLong();

    @Idempotent(bizType = "DEV_IDEM_PROBE")
    @PostMapping("/idem-probe")
    public Result<Map<String, Object>> idemProbe(@RequestBody IdemProbeDTO body) {
        //1.能进来说明幂等检查已通过，业务执行次数 +1
        long count = EXECUTE_COUNT.incrementAndGet();
        //2.fail=true 模拟业务失败：切面会删占位放行重试（测试验证项）
        if (body.isFail()) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "探针模拟业务失败");
        }
        //3.回显执行次数，正常返回
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", body.getName());
        data.put("executeCount", count);
        return Result.ok(data);
    }

    /** 探针请求体：name 回显用、fail 控制是否模拟业务失败 */
    @Data
    public static class IdemProbeDTO {
        private String name;
        private boolean fail;
    }
}
