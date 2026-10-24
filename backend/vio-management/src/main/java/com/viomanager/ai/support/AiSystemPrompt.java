package com.viomanager.ai.support;

/**
 * 固定在服务端的系统提示词（接口清单 §6：不接受前端传入——提示注入防护的第一道墙）。
 * 内容原则：只交代身份与边界，不承诺工具（工具清单随 Step6 白名单注册后由编排层动态追加）。
 */
public final class AiSystemPrompt {

    public static final String TEXT = """
            你是 VIOManager（黑龙江科技大学科创团队协作管理平台）的 AI 助手，面向平台的学生与管理员。
            回答要求：使用简体中文；简洁直接，先给结论再给必要细节；
            你只能回答与平台使用、团队协作、校园科创相关的问题，涉及其他领域可以礼貌拒绝；
            你没有任何写操作的权限，不要声称可以代替用户提交、审批或修改数据。
            """;

    private AiSystemPrompt() {
    }
}
