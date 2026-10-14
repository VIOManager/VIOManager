package com.viomanager.common.exception;

import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.mybatis.spring.MyBatisSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器：把一切异常统一收敛为 Result 响应体，全组接口自动受益。
 * 业务码透传、参数类错误归 1001、路由未命中归 1004、未捕获异常兜底 1000。
 * 业务代码永远不写 try-catch 再拼错误响应，直接 throw BizException 即可。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常：code 与 message 原样透传。
     * ⚠️ 冲突：1002 必须配 HTTP 401、1003 必须配 HTTP 403——前端拦截器按
     * HTTP 状态跳登录页/提示无权限（规范 §5.1）；去掉这对映射前端跳转会全部失效。
     * 其余业务码一律 HTTP 200，前端只认响应体里的 code。
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        return toResponse(e);
    }

    /**
     * MyBatis 包装异常解包：拦截器在 SQL 执行期抛的 BizException（如数据范围拒绝的 1003）
     * 会被 MyBatis 包成 MyBatisSystemException，这里把业务异常解出来按原码响应，
     * 不解包的话全落 1000 兜底，前端拿不到真实错误码。
     */
    @ExceptionHandler(MyBatisSystemException.class)
    public ResponseEntity<Result<Void>> handleMybatisWrapped(MyBatisSystemException e) {
        BizException biz = BizException.unwrap(e);
        if (biz != null) {
            return toResponse(biz);
        }
        log.error("未捕获的 MyBatis 系统异常：{}", e.getMessage(), e);
        return ResponseEntity.ok(Result.fail(CommonErrorCode.INTERNAL_ERROR));
    }

    /**
     * 业务异常转响应：默认 HTTP 200 + 响应体 code 表达；
     * 1002 配 401、1003 配 403 是规范 §5.1 定的配对，前端拦截器靠它跳登录页。
     */
    private ResponseEntity<Result<Void>> toResponse(BizException e) {
        HttpStatus status = HttpStatus.OK;
        if (e.getCode() == CommonErrorCode.UNAUTHORIZED.getCode()) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (e.getCode() == CommonErrorCode.FORBIDDEN.getCode()) {
            status = HttpStatus.FORBIDDEN;
        }
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 参数绑定校验失败（含 @RequestBody @Valid 触发的 MethodArgumentNotValidException，其子类）。
     * 规范要求 1001 的 message 说明哪个字段，这里逐字段拼出中文说明。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        StringBuilder detail = new StringBuilder();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            if (detail.length() > 0) {
                detail.append("；");
            }
            detail.append(fieldError.getField()).append(" ").append(fieldError.getDefaultMessage());
        }
        return Result.fail(CommonErrorCode.PARAM_INVALID, "参数校验失败：" + detail);
    }

    /** 单参数校验失败（@Validated + 方法参数约束）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        StringBuilder detail = new StringBuilder();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            if (detail.length() > 0) {
                detail.append("；");
            }
            // M2 脱敏：propertyPath 带完整调用路径（含 Java 方法名，如 bizError.name），
            // 直接回显等于把服务端方法结构画给攻击者——只留最后一段字段名
            String path = String.valueOf(violation.getPropertyPath());
            String fieldName = path.substring(path.lastIndexOf('.') + 1);
            detail.append(fieldName).append(" ").append(violation.getMessage());
        }
        return Result.fail(CommonErrorCode.PARAM_INVALID, "参数校验失败：" + detail);
    }

    /** 请求方法不支持（如对 POST 接口发 GET）：收敛为 1001，不再落 1000 兜底误导排查。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.fail(CommonErrorCode.PARAM_INVALID, "请求方法不支持：" + e.getMethod());
    }

    /** Content-Type 不支持（如非 multipart 上传被拒时）：收敛为 1001。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Result<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return Result.fail(CommonErrorCode.PARAM_INVALID, "请求的 Content-Type 不支持");
    }

    /** 缺少必填请求参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(CommonErrorCode.PARAM_INVALID, "参数校验失败：缺少必填参数 " + e.getParameterName());
    }

    /** 请求参数类型不匹配（如 id 传了非数字）。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(CommonErrorCode.PARAM_INVALID, "参数校验失败：参数 " + e.getName() + " 类型不正确");
    }

    /**
     * 请求体解析失败（JSON 语法错 / 字段类型不对 / 时间格式不合规）：收敛为 1001。
     * 立此存照：2026-10-14 创建比赛时前端按规范发空格格式日期，落 1000"系统繁忙"
     * 误导排查半小时——解析类失败必须说人话，不能进兜底。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return Result.fail(CommonErrorCode.PARAM_INVALID,
                "请求体格式不正确（检查字段类型与时间格式 yyyy-MM-dd HH:mm:ss）");
    }

    /** 路由未命中：统一收敛为 1004，避免 Spring 默认错误体破坏响应契约。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        return Result.fail(CommonErrorCode.NOT_FOUND);
    }

    /**
     * 上传超过 Spring multipart 硬限制（yml 的 max-file-size，刻意高于业务限制）：
     * 收敛为 1009，与文件服务自己的大小校验同一个错误码，前端不用区分两层限制。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return Result.fail(CommonErrorCode.FILE_TOO_LARGE, "文件大小超出限制");
    }

    /**
     * 兜底：一切未捕获异常。
     * ⚠️ 难点：完整堆栈必须落日志（排查唯一线索），但绝不能进响应体——
     * 堆栈含类名与 SQL 片段，外泄等于给攻击者画地图。
     */
    @ExceptionHandler(Throwable.class)
    public Result<Void> handleUnknown(Throwable e) {
        log.error("未捕获异常：{}", e.getMessage(), e);
        return Result.fail(CommonErrorCode.INTERNAL_ERROR);
    }
}
