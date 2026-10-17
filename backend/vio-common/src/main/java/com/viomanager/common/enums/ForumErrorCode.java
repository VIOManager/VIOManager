package com.viomanager.common.enums;

/**
 * 9100 段论坛错误码（规范 §4 段位表：9100-9199 归论坛服务，9003-9099 留知识库/AI 续段）。
 * 码值与含义照《论坛-独立库与骨架-设计方案》§四；管理操作权限不占业务码，用通用 1003。
 *
 * ⚠️ 本文件位于 common.enums 属**公共组件**：按规范 §7.6，开 PR 前须在群里报备。
 */
public enum ForumErrorCode implements ErrorCode {

    /** 回帖守卫：主题被管理员锁定（状态机 NORMAL⇄LOCKED，LOCKED 禁回）。 */
    TOPIC_LOCKED(9101, "主题被锁定，禁止回帖"),

    /** 主题不存在、已隐藏或已删除（HIDDEN 对所有人不可见）。 */
    TOPIC_NOT_FOUND(9102, "主题不存在或已隐藏"),

    /** 楼层不存在、已隐藏或已删除。 */
    REPLY_NOT_FOUND(9103, "楼层不存在或已隐藏"),

    /** 发帖守卫：版块不存在或已停用（DISABLED 禁发新帖，旧帖可见）。 */
    BOARD_DISABLED(9104, "版块不存在或已停用"),

    /** 二级评论守卫：父评论不存在/已隐藏/不属于该主题（2026-10-17 两级评论拍板）。 */
    PARENT_REPLY_INVALID(9105, "父评论不存在、已隐藏或不属于该主题"),

    /** 两级封顶：不允许对二级评论再评论。 */
    REPLY_LEVEL_INVALID(9106, "评论最多两级，不能回复二级评论");

    // 9110/9111（推送不存在/重复收藏）随技术推送域于 2026-10-15 退役
    // （方案 2026-10-15/技术交流-并入论坛），码段保留不再复用。

    private final int code;
    private final String message;

    ForumErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
