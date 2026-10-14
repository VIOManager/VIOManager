package com.viomanager.kb.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * KB07 我的收藏列表项（时间倒序，带文档摘要）。
 */
@Data
public class KbCollectVO {

    private Long docId;

    private String title;

    private String category;

    private String summary;

    private String sourceType;

    /** 收藏时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime collectedAt;
}
