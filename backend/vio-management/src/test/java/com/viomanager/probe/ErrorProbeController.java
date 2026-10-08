package com.viomanager.probe;

import com.viomanager.common.enums.ApprovalErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅测试用探针：触发各类异常，验证全局异常处理器的统一响应。
 * 位于 src/test，不会进入运行时 jar 与生产镜像。
 */
@RestController
@RequestMapping("/api/test")
public class ErrorProbeController {

    /** 触发业务异常：6001 透传。 */
    @GetMapping("/biz-error")
    public Result<Void> bizError() {
        throw new BizException(ApprovalErrorCode.NOT_FOUND_OR_HANDLED);
    }

    /** 触发未捕获异常：兜底 1000。 */
    @GetMapping("/runtime-error")
    public Result<Void> runtimeError() {
        throw new IllegalStateException("模拟未捕获异常");
    }

    /** 触发请求体校验失败：1001 并说明字段。 */
    @PostMapping("/validated")
    public Result<Void> validated(@Valid @RequestBody ProbeDto dto) {
        return Result.ok();
    }

    /** 触发缺参：1001 并说明参数名。 */
    @GetMapping("/missing-param")
    public Result<Void> missingParam(@RequestParam String name) {
        return Result.ok();
    }

    /** 测试用请求体，普通类 + getter/setter。 */
    public static class ProbeDto {

        @NotBlank(message = "不能为空")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
