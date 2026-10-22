package com.viomanager.topic.vo;

import lombok.Data;

/**
 * F02 今日热议条目（top5）。
 */
@Data
public class HotTopicVO {

    /** 主题 ID */
    private Long id;

    /** 标题 */
    private String title;

    /** 作者姓名快照 */
    private String author;
}
