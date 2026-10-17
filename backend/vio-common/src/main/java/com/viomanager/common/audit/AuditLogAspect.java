package com.viomanager.common.audit;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.config.AuditProperties;
import com.viomanager.common.util.IpUtil;
import com.viomanager.common.util.ParamsJsonUtil;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计切面：@AuditLog 声明的接口，调用自动写 audit_log（规范 §5.4 最后一道关口）。
 * 机制：
 *  1. 请求线程里一次性抓快照（用户身份、请求信息、参数摘要）——异步线程拿不到这些上下文
 *  2. 业务成功/失败/耗时全部记录；失败不吞异常，原样抛给全局异常处理器
 *  3. 写库交给后台单线程，接口耗时不受影响（vio.audit.async=false 可切同步，测试用）
 * 运行顺序刻意最外层（@Order(0)）：被 @RequireRole 拒掉、被幂等拦掉的调用同样留痕——
 * "谁试图干什么但被拒了"本身就是审计要的信息。
 * ⚠️ 阶段2 论坛服务引同一 jar 即用，各服务写各自的库。
 */
@Slf4j
@Aspect
@Component
@Order(0)
public class AuditLogAspect {

    /** 各列宽度上限（与 audit_log 表列宽一致），超长截断 */
    private static final int USER_AGENT_MAX = 300;
    private static final int URI_MAX = 500;
    private static final int SUMMARY_MAX = 500;
    private static final int REQUEST_PARAMS_MAX = 2000;
    private static final int ERROR_MESSAGE_MAX = 1000;

    /** SpEL 解析器（线程安全，静态复用） */
    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();

    @Resource
    private AuditLogDao auditLogDao;

    @Resource
    private AuditProperties auditProperties;

    /** 方法参数名发现器（SpEL 的 #参数名 依赖它；Spring Boot 3 默认开启 -parameters 编译） */
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /** 审计写库后台单线程：守护线程不阻碍 JVM 退出，线程名方便排查 */
    private final ExecutorService auditWriter = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "audit-writer");
            thread.setDaemon(true);
            return thread;
        }
    });

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        //1.总开关关闭：审计完全不介入，业务照跑（排查性能问题时用）
        if (!auditProperties.isEnabled()) {
            return joinPoint.proceed();
        }
        long start = System.currentTimeMillis();
        //2.进业务前先抓快照：用户身份、请求信息、参数摘要
        AuditRecord record = capture(joinPoint, auditLog);
        //3.执行业务：成败都留痕，异常原样上抛
        try {
            Object result = joinPoint.proceed();
            record.setSuccess(true);
            record.setCostMillis(System.currentTimeMillis() - start);
            write(record);
            return result;
        } catch (Throwable businessError) {
            record.setSuccess(false);
            record.setErrorMessage(truncate(businessError.getMessage(), ERROR_MESSAGE_MAX));
            record.setCostMillis(System.currentTimeMillis() - start);
            write(record);
            throw businessError;
        }
    }

    /** 在请求线程里抓全一条审计记录（异步线程拿不到 ThreadLocal 与请求上下文） */
    private AuditRecord capture(ProceedingJoinPoint joinPoint, AuditLog auditLog) {
        AuditRecord record = new AuditRecord();
        record.setId(IdWorker.getId());
        //1.操作人信息：来自 token 验签结果（匿名场景为空，列本来就允许空）
        CurrentUser user = UserHolder.getUser();
        if (user != null) {
            record.setUserId(user.getUserId());
            record.setLoginName(user.getLoginName());
            record.setRealName(user.getRealName());
            record.setRole(user.getRole());
            record.setOrgId(user.getOrgId());
        }
        //2.请求信息：IP 逐跳解析代理头，UA 与路径取当前请求
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            record.setIp(IpUtil.resolveClientIp(request));
            record.setUserAgent(truncate(request.getHeader("User-Agent"), USER_AGENT_MAX));
            record.setHttpMethod(request.getMethod());
            record.setUri(truncate(request.getRequestURI(), URI_MAX));
        }
        //3.业务标识与摘要
        record.setBizType(auditLog.bizType());
        record.setBizTypeLabel(auditLog.label().isEmpty() ? auditLog.bizType() : auditLog.label());
        record.setSummary(truncate(renderSummary(auditLog.summary(), joinPoint), SUMMARY_MAX));
        //4.请求参数摘要：敏感接口（密码、身份证相关）留空，防敏感值进日志
        if (!auditLog.sensitive()) {
            record.setRequestParams(truncate(ParamsJsonUtil.toJson(joinPoint.getArgs()), REQUEST_PARAMS_MAX));
        }
        return record;
    }

    /**
     * 摘要渲染：不含 # 号按纯文本存（大多数人只需要固定文案）；
     * 含 # 号整句按 SpEL 解析（如 "'创建赛队：' + #body.teamName"）。
     * 写错降级存原文——审计摘要的瑕疵不能影响业务。
     */
    private String renderSummary(String template, ProceedingJoinPoint joinPoint) {
        if (template == null || template.isEmpty() || template.indexOf('#') < 0) {
            return template;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    null, method, joinPoint.getArgs(), parameterNameDiscoverer);
            return SPEL_PARSER.parseExpression(template).getValue(context, String.class);
        } catch (Exception e) {
            log.warn("审计摘要 SpEL 渲染失败，按原文存：{}，原因：{}", template, e.getMessage());
            return template;
        }
    }

    /** 落库：异步交给后台单线程，接口不等写库；同步模式（测试/排查）立即可查 */
    private void write(AuditRecord record) {
        if (auditProperties.isAsync()) {
            // 把写库任务交给后台线程，请求线程立刻返回
            auditWriter.submit(() -> auditLogDao.insert(record));
        } else {
            auditLogDao.insert(record);
        }
    }

    /** 超长截断（null 安全） */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
