package com.viomanager.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.viomanager.common.auth.CurrentUser;

/**
 * AI 工具白名单的单元（接口清单 §2：只注册查询类，红线"AI 只查不写"）。
 * 实现类自行声明 OpenAI Function Calling 的名称/描述/参数 Schema，
 * execute 返回结果 JSON 字符串——只作数据拼进模型上下文，绝不被执行（提示注入防护）。
 * 工具以当前用户身份执行（调用方负责 UserHolder 上下文），数据范围与本人直调一致。
 */
public interface AiTool {

    /** 工具名（白名单 key，模型按名调用） */
    String name();

    /** 给模型看的功能描述 */
    String description();

    /** 参数 JSON Schema（{"type":"object",...}） */
    String parametersSchema();

    /**
     * 执行查询并返回结果 JSON（给模型的数据）。
     * 实现内部可以抛业务异常——由调用方兜底转 {"error":"..."}，失败不炸 SSE 流。
     */
    String execute(JsonNode args, CurrentUser user) throws Exception;
}
