package com.viomanager.approval.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Component;

/**
 * 审批快照出参净化（P09 详情）。
 * 前端拿到 bizPayload 字符串后 JSON.parse 展示，超过 JS 安全整数上限 2^53-1 的
 * 雪花 ID 会被静默舍入失真（B/C 线联调报告 #4：详情快照里的队 ID 末位变 0，
 * 显示层查无此数）。审批中心是快照的唯一出口，出口处统一把超范围整数转字符串，
 * 各业务线构造 payload 时是否字符串化不再影响显示正确性；
 * DB 内原值不动，回调 Handler 仍按机器契约读数。
 */
@Component
public class ApprovalPayloadSupport {

    /** JS Number.MAX_SAFE_INTEGER，超过即无法被前端安全表示 */
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 超过 JS 安全范围的整数转字符串，其余结构与数值原样；非合法 JSON 原样返回（展示层兜底） */
    public String sanitize(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        try {
            return clean(MAPPER.readTree(payload)).toString();
        } catch (Exception e) {
            return payload;
        }
    }

    /** 递归复制节点，命中超范围整数的叶子换成文本节点 */
    private JsonNode clean(JsonNode node) {
        if (node.isObject()) {
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(e -> out.set(e.getKey(), clean(e.getValue())));
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            node.forEach(v -> out.add(clean(v)));
            return out;
        }
        //BigInteger 节点必然放不进 long，安全范围判断对其无意义，短路直接转
        if (node.isIntegralNumber()
                && (node.isBigInteger() || Math.abs(node.longValue()) > MAX_SAFE_INTEGER)) {
            return new TextNode(node.asText());
        }
        return node;
    }
}
