package com.viomanager.contest.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 外部比赛待处理池实体，对应表 contest_source。
 * 状态：PENDING 待处理 / CONFIRMED 已转为比赛 / IGNORED 已忽略。
 * confirmed_contest_id：确认后生成的比赛 ID 回填。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("contest_source")
public class ContestSource extends BaseEntity {

    /** 抓取到的比赛标题 */
    private String title;

    /** 来源类型：CRAWLER / RSS / MANUAL */
    private String sourceType;

    /** 来源地址 */
    private String sourceUrl;

    /** 来源侧唯一标识，用于去重 */
    private String sourceKey;

    /** 去重键：source_key + title + contest_time 的哈希 */
    private String dedupKey;

    /** 原始抓取内容，供管理员判断 */
    private String rawContent;

    /** 解析出的比赛时间 */
    private LocalDateTime contestTime;

    /** 解析出的规则线索 JSON */
    private String parsedRules;

    /** 状态：PENDING / CONFIRMED / IGNORED */
    private String status;

    /** 确认后生成的比赛 ID */
    private Long confirmedContestId;

    /** 抓取时间 */
    private LocalDateTime fetchedAt;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 处理人 */
    private Long handledBy;
}
