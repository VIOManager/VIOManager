package com.viomanager.approval.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 回调注册表：收齐所有 ApprovalHandler Bean，按 bizType 建索引。
 * Spring 构造注入 List&lt;ApprovalHandler&gt; 会把所有实现一次性给到，
 * 以后 B 线写个 TeamCreateHandler 注册成 Bean，这里自动就收录，无需改骨架代码。
 */
@Component
public class ApprovalHandlerRegistry {

    private final Map<String, ApprovalHandler> handlers = new HashMap<>();

    /** 兜底：业务类型没注册时用它，记日志不动作 */
    private final ApprovalHandler fallback = new FallbackApprovalHandler();

    public ApprovalHandlerRegistry(List<ApprovalHandler> handlerBeans) {
        for (ApprovalHandler handler : handlerBeans) {
            String type = handler.bizType();
            // 空类型是兜底自己或误用，跳过不注册
            if (type != null && !type.isEmpty()) {
                handlers.put(type, handler);
            }
        }
    }

    /** 按业务类型取 Handler，没注册返回兜底 */
    public ApprovalHandler get(String bizType) {
        ApprovalHandler handler = handlers.get(bizType);
        if (handler == null) {
            return fallback;
        }
        return handler;
    }
}
