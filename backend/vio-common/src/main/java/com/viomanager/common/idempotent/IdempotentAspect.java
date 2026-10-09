package com.viomanager.common.idempotent;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.config.IdempotentProperties;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.common.util.ParamsJsonUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 幂等拦截切面：标了 @Idempotent 的写接口，先占幂等键再执行业务（规范 §6 写操作防护）。
 * 机制：
 *  1. 请求头 X-Idempotency-Key 必填，缺失 1001
 *  2. 同 key 未过期 → 1005 重复提交（K3 拍板：拦截而非回放首次结果，表里只存摘要回放不了）
 *  3. 先插占位记录再执行业务——并发双击时只有一个请求插得进唯一键 uk_idem_key，
 *     另一个转 1005，保证业务方法绝对只执行一次
 *  4. 业务抛异常 → 删占位放行重试；业务成功 → 回填结果摘要
 * ⚠️ 阶段2 论坛服务直接复用本切面，各服务用各自的数据源，键互不串
 */
@Slf4j
@Aspect
@Component
public class IdempotentAspect {

    /** 幂等键请求头名（规范 §6 全组统一） */
    public static final String IDEMPOTENT_HEADER = "X-Idempotency-Key";

    /** result_summary 列宽 500，摘要超长截断 */
    private static final int SUMMARY_MAX_LENGTH = 500;

    @Resource
    private IdempotentRecordDao idempotentRecordDao;

    @Resource
    private IdempotentProperties idempotentProperties;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        //1.拿当前请求；不在 Web 环境（定时任务等内部调用）谈不上幂等，放行并留痕
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.warn("@Idempotent 用在非 Web 方法上，跳过幂等：{}", joinPoint.getSignature());
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();
        //2.幂等键必填（规范 §6：创建、审批、提交类接口必须带）
        String idemKey = request.getHeader(IDEMPOTENT_HEADER);
        if (idemKey == null || idemKey.trim().isEmpty()) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "缺少请求头 X-Idempotency-Key");
        }
        idemKey = idemKey.trim();
        //3.请求体哈希：同 key 不同参属于前端异常使用，存档备查（拦截行为仍只看 key）
        String requestHash = sha256Hex(ParamsJsonUtil.toJson(joinPoint.getArgs()));
        //4.查历史：未过期直接 1005；已过期删掉，key 可复用（方案 Step 5 验证项）
        Map<String, Object> existing = idempotentRecordDao.findByKey(idemKey);
        if (existing != null) {
            boolean expired = ((Number) existing.get("expired")).intValue() == 1;
            if (!expired) {
                String storedHash = (String) existing.get("request_hash");
                if (storedHash != null && !storedHash.equals(requestHash)) {
                    log.warn("幂等键同键不同参（疑似前端键生成随机数不足）：key={}, bizType={}",
                            idemKey, idempotent.bizType());
                }
                throw new BizException(CommonErrorCode.DUPLICATE_SUBMIT);
            }
            idempotentRecordDao.deleteByKey(idemKey);
        }
        //5.占坑：先插占位记录再执行业务；撞唯一键说明并发对手先到，转 1005
        CurrentUser user = UserHolder.getUser();
        Long userId = user == null ? null : user.getUserId();
        LocalDateTime expireAt = LocalDateTime.now().plusHours(idempotentProperties.getExpireHours());
        try {
            idempotentRecordDao.insertRecord(IdWorker.getId(), idemKey, idempotent.bizType(),
                    userId, requestHash, expireAt);
        } catch (DuplicateKeyException e) {
            throw new BizException(CommonErrorCode.DUPLICATE_SUBMIT);
        }
        //6.执行业务：成功回填摘要，失败删占位放行重试
        try {
            Object result = joinPoint.proceed();
            idempotentRecordDao.updateSummary(idemKey, summarize(result));
            return result;
        } catch (Throwable businessError) {
            try {
                idempotentRecordDao.deleteByKey(idemKey);
            } catch (Exception cleanupError) {
                // 清理失败只能让 key 占用到过期为止，不能反过来吞掉业务异常
                log.error("幂等占位清理失败，key={} 将占用至过期：{}", idemKey, cleanupError.getMessage());
            }
            throw businessError;
        }
    }

    /** 首次执行结果摘要："code=0,message=ok,data=..." 形态，超长截断到列宽 */
    private String summarize(Object result) {
        String text;
        if (result instanceof Result) {
            Result<?> response = (Result<?>) result;
            text = "code=" + response.getCode() + ",message=" + response.getMessage()
                    + ",data=" + ParamsJsonUtil.toJson(response.getData());
        } else {
            text = ParamsJsonUtil.toJson(result);
        }
        if (text != null && text.length() > SUMMARY_MAX_LENGTH) {
            text = text.substring(0, SUMMARY_MAX_LENGTH);
        }
        return text;
    }

    /** SHA-256 十六进制（64 字符，正好对齐 request_hash 列宽） */
    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < bytes.length; i++) {
                String hex = Integer.toHexString(bytes[i] & 0xFF);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必带 SHA-256，理论到不了这里
            throw new IllegalStateException("SHA-256 摘要算法不可用", e);
        }
    }
}
