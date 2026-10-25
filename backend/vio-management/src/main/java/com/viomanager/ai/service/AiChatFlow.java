package com.viomanager.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.viomanager.ai.client.GlmClient;
import com.viomanager.ai.client.GlmSseParser;
import com.viomanager.ai.client.GlmStreamEvent;
import com.viomanager.ai.config.AiProperties;
import com.viomanager.ai.dto.ChatDTO;
import com.viomanager.ai.entity.AiMessage;
import com.viomanager.ai.support.AiSystemPrompt;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.ai.tool.AiToolRegistry;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 一次 chat 的多轮生成循环（接口清单 §5：tool start/done 可多轮）：
 * 上游回 tool_calls → 以当前用户身份执行白名单工具 → 结果追加为 tool 消息 → 再请求，
 * 直到 stop/length 或达轮次上限（防模型死循环）。
 * 红线执行点：工具只查不写（白名单内全为查询）；工具结果只作数据拼接不执行；
 * 工具执行时临时挂 UserHolder（异步线程无 ThreadLocal），完毕即摘除。
 */
@Component
public class AiChatFlow {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 最多 5 轮上游交互（1 轮生成 + 至多 4 轮工具往返） */
    private static final int MAX_ROUNDS = 5;

    @Resource
    private AiToolRegistry toolRegistry;

    @Resource
    private AiProperties properties;

    /** 一次完整生成的聚合结果；error 非 null = 中途异常（reply 为已生成部分，落库标 incomplete=1） */
    public record FlowResult(String reply, String finishReason, long promptTokens,
                             long completionTokens, Exception error) {
    }

    public FlowResult run(GlmClient client, ChatDTO dto, List<AiMessage> history,
                          CurrentUser user, SseEmitter emitter, AtomicBoolean cancelled) {
        ArrayNode messages = MAPPER.createArrayNode();
        //系统提示词 = 固定框架 + 当前用户身份段（服务端注入，模型据此知道在和谁说话）
        messages.addObject().put("role", "system").put("content", AiSystemPrompt.forUser(user));
        for (AiMessage message : history) {
            messages.addObject().put("role", message.getRole()).put("content", message.getContent());
        }
        messages.addObject().put("role", "user").put("content", dto.getContent());

        StringBuilder reply = new StringBuilder();
        long promptTotal = 0;
        long completionTotal = 0;
        String finishReason = null;
        try {
            for (int round = 0; round < MAX_ROUNDS; round++) {
                GlmSseParser parser = new GlmSseParser();
                GlmClient.StreamHandle handle = client.prepareStream(
                        buildRequestBody(client, messages),
                        line -> consumeLine(emitter, parser, reply, line));
                handle.start();
                promptTotal += nullSafe(parser.getPromptTokens());
                completionTotal += nullSafe(parser.getCompletionTokens());
                finishReason = parser.getFinishReason();
                if (!"tool_calls".equals(finishReason) || parser.toolCalls().isEmpty()) {
                    break;  //终轮：done 事件已在 consumeLine 发出
                }
                for (GlmSseParser.ToolCall call : parser.toolCalls()) {
                    sendToolEvent(emitter, call.name(), "start");
                    String result = executeToolSafe(call, user);
                    sendToolEvent(emitter, call.name(), "done");
                    appendAssistantToolCall(messages, call);
                    appendToolResult(messages, call, result);
                }
            }
            return new FlowResult(reply.toString(), finishReason, promptTotal, completionTotal, null);
        } catch (Exception e) {
            //中途异常（含客户端断开导致的读流失败）：已生成部分照常交给调用方落库
            return new FlowResult(reply.toString(), finishReason, promptTotal, completionTotal, e);
        }
    }

    /** 上游每一行：文本增量发 delta；[DONE] 发 done（工具轮不发——还有下一轮） */
    private void consumeLine(SseEmitter emitter, GlmSseParser parser, StringBuilder reply, String line) {
        GlmStreamEvent event = parser.accept(line);
        if (event == null) {
            return;
        }
        if (event.text() != null) {
            reply.append(event.text());
            ObjectNode delta = MAPPER.createObjectNode();
            delta.put("text", event.text());
            sendEvent(emitter, "delta", delta);
        }
        if (event.end() && !"tool_calls".equals(parser.getFinishReason())) {
            String finish = "length".equals(parser.getFinishReason()) ? "interrupted" : "stop";
            ObjectNode done = MAPPER.createObjectNode();
            done.put("finishReason", finish);
            ObjectNode usage = done.putObject("usage");
            usage.put("prompt", nullSafe(parser.getPromptTokens()));
            usage.put("completion", nullSafe(parser.getCompletionTokens()));
            sendEvent(emitter, "done", done);
        }
    }

    /** 白名单工具执行：以当前用户身份，任何失败转错误 JSON 给模型（不炸流） */
    private String executeToolSafe(GlmSseParser.ToolCall call, CurrentUser user) {
        try {
            AiTool tool = toolRegistry.byName(call.name()).orElse(null);
            if (tool == null) {
                return "{\"error\":\"工具不在白名单：\"}" + call.name();
            }
            JsonNode args = call.arguments() == null || call.arguments().isBlank()
                    ? MAPPER.createObjectNode() : MAPPER.readTree(call.arguments());
            UserHolder.saveUser(user);  //异步线程无 ThreadLocal：工具查询以真实登录人身份跑
            try {
                return tool.execute(args, user);
            } finally {
                UserHolder.removeUser();
            }
        } catch (Exception e) {
            return "{\"error\":\"" + safeMessage(e) + "\"}";
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.replace("\"", "'").replace('\n', ' ');
    }

    /** 模型点名的工具调用回放（assistant 消息带完整 tool_calls） */
    private void appendAssistantToolCall(ArrayNode messages, GlmSseParser.ToolCall call) {
        ObjectNode assistant = messages.addObject();
        assistant.put("role", "assistant");
        assistant.putNull("content");
        ObjectNode toolCall = assistant.putArray("tool_calls").addObject();
        toolCall.put("id", call.id());
        toolCall.put("type", "function");
        ObjectNode fn = toolCall.putObject("function");
        fn.put("name", call.name());
        fn.put("arguments", call.arguments() == null ? "{}" : call.arguments());
    }

    /** 工具结果消息（role=tool）：内容是查询结果 JSON，只作数据拼进上下文 */
    private void appendToolResult(ArrayNode messages, GlmSseParser.ToolCall call, String result) {
        ObjectNode toolMessage = messages.addObject();
        toolMessage.put("role", "tool");
        toolMessage.put("tool_call_id", call.id() == null ? "" : call.id());
        toolMessage.put("content", result);
    }

    private void sendToolEvent(SseEmitter emitter, String name, String phase) {
        ObjectNode tool = MAPPER.createObjectNode();
        tool.put("name", name);
        tool.put("phase", phase);
        sendEvent(emitter, "tool", tool);
    }

    /** OpenAI 兼容请求体：系统提示词服务端固定；白名单 tools；max_tokens 拍板 2048 */
    private String buildRequestBody(GlmClient client, ArrayNode messages) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", client.getModel());
        root.set("messages", messages);
        root.put("stream", true);
        root.put("max_tokens", properties.getMaxCompletionTokens());
        root.putObject("stream_options").put("include_usage", true);
        root.set("tools", toolRegistry.toolsDefinition());
        return root.toString();
    }

    private void sendEvent(SseEmitter emitter, String name, ObjectNode data) {
        try {
            emitter.send(SseEmitter.event().name(name)
                    .data(data.toString(), MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static long nullSafe(Long value) {
        return value == null ? 0 : value;
    }
}
