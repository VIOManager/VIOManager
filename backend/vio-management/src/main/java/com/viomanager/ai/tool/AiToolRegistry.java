package com.viomanager.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * AI 工具白名单注册表：Spring 注入所有 AiTool Bean，此处聚合成
 * ① 按名查找（模型点名的工具只有白名单内的才执行）
 * ② OpenAI tools 请求段定义（随每轮 chat 请求下发）。
 * 新增工具 = 实现一个 @Component AiTool，自动进白名单（清单 §2"工具名以后端定名为准回填"）。
 */
@Component
public class AiToolRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, AiTool> tools;

    public AiToolRegistry(List<AiTool> toolBeans) {
        this.tools = toolBeans.stream()
                .collect(Collectors.toUnmodifiableMap(AiTool::name, Function.identity()));
    }

    /** 按名取白名单工具；模型点名了未注册的名字返回 empty（调用方回错误 JSON） */
    public Optional<AiTool> byName(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /** OpenAI 兼容请求的 tools 段（无工具时返回空数组，请求体可不带） */
    public ArrayNode toolsDefinition() {
        ArrayNode array = MAPPER.createArrayNode();
        for (AiTool tool : tools.values()) {
            ObjectNode entry = array.addObject();
            entry.put("type", "function");
            ObjectNode fn = entry.putObject("function");
            fn.put("name", tool.name());
            fn.put("description", tool.description());
            fn.set("parameters", parseSchema(tool.parametersSchema()));
        }
        return array;
    }

    private JsonNode parseSchema(String schema) {
        try {
            return MAPPER.readTree(schema);
        } catch (Exception e) {
            //Schema 写错属编码期问题：上线前测试必炸，这里兜底空 schema 不挡启动
            return MAPPER.createObjectNode().put("type", "object");
        }
    }
}
