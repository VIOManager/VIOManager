package com.viomanager.ai.support;

import com.viomanager.common.auth.CurrentUser;

/**
 * 固定在服务端的系统提示词（接口清单 §6：不接受前端传入——提示注入防护的第一道墙）。
 * 用户身份段来自 JWT 验签后的 CurrentUser（服务端注入，非请求参数，不可伪造）——
 * 没有它模型不知道"我是谁"，会把已登录用户答成未登录。
 */
public final class AiSystemPrompt {

    public static final String TEXT = """
            你是 VIOManager（黑龙江科技大学科创团队协作管理平台）的 AI 助手，面向平台的学生与管理员。
            回答要求：使用简体中文；简洁直接，先给结论再给必要细节；
            你只能回答与平台使用、团队协作、校园科创相关的问题，涉及其他领域可以礼貌拒绝；
            你没有任何写操作的权限，不要声称可以代替用户提交、审批或修改数据。
            需要查询数据时直接调用工具，不要在回复文本中展示工具名、调用代码或中间过程，
            最终回复只包含面向用户的自然语言结论。
            """;

    /** 身份段：拼在固定提示词后，让模型知道在和谁说话（角色中文化 + 数据查询倾向） */
    public static String forUser(CurrentUser user) {
        String name = user.getRealName() != null && !user.getRealName().isBlank()
                ? user.getRealName() : user.getLoginName();
        return TEXT + "当前登录用户是：" + name + "（" + roleCn(user.getRole()) + "）。"
                + "用户已通过平台登录，询问本人相关信息时优先调用查询工具获取后回答。\n";
    }

    private static String roleCn(String role) {
        return switch (role == null ? "" : role) {
            case "SUPER_ADMIN" -> "超级管理员";
            case "DEPT_ADMIN" -> "部门管理员";
            case "STUDENT" -> "学生";
            default -> "用户";
        };
    }

    private AiSystemPrompt() {
    }
}

