package com.viomanager.topic.vo;

import lombok.Data;

/**
 * F03 社区运行状况。
 * memberCount 语义 = 在论坛有过写操作的账号数（forum_user 行数，方案 §3.4）。
 * 计数字段刻意用 int：Long 会被全局序列化为字符串，与前端 TS number 契约不符
 * （审查 L10 口径，lzh 2026-10-16 拍板改后端出真数字）。
 */
@Data
public class ForumStatsVO {

    /** 论坛成员数（forum_user 快照行数） */
    private int memberCount;

    /** 主题数（未删未隐藏） */
    private int topicCount;

    /** 回复数（未删） */
    private int replyCount;
}
