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

    private String summary;

    /** 条目形态：LINK / FILE */
    private String sourceType;

    /** 外部链接（LINK 时有值） */
    private String linkUrl;

    /** 附件文件 ID（FILE 时有值） */
    private Long fileId;

    /** 附件原始文件名（FILE 时有值） */
    private String fileName;

    /** 收藏时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime collectedAt;
}
