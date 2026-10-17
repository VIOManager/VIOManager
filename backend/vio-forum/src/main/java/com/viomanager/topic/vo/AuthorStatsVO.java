package com.viomanager.topic.vo;

import lombok.Data;

/**
 * 帖子详情右栏"关于作者"统计（方案 §3.5：likeTotal 只算主题获赞，第三项为被收藏数）。
 * 计数字段刻意用 int：全局 Jackson 把 Long 序列化为字符串（雪花 ID 精度保护），
 * 计数转字符串与前端 TS number 契约不符（审查 L10 口径，lzh 2026-10-16 拍板改后端出真数字）。
 */
@Data
public class AuthorStatsVO {

    /** 发帖数（该作者主题数） */
    private int postCount;

    /** 获赞总数（该作者主题 like_count 求和，楼层赞不计） */
    private int likeTotal;

    /** 被收藏数（该作者主题 collect_count 求和；前端原 archivedCount 已改名） */
    private int collectedCount;
}
