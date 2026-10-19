package com.viomanager.project.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 查看授权列表项（PR30）：谁申请过、类型、状态、到期时间、已用次数。
 */
@Data
public class ViewGrantVO {

    /** project_view_grant 行 ID */
    private Long id;

    /** 申请人姓名 */
    private String applicantName;

    /** 申请人组织 */
    private Long applicantOrgId;

    /** 授权类型：ONCE / LIMITED */
    private String grantType;

    /** 状态：PENDING / ACTIVE / EXPIRED / REVOKED */
    private String status;

    /** 限时截止（ONCE 为空） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireAt;

    /** 已使用次数（ONCE 消耗即 +1） */
    private Integer usedCount;

    /** 申请时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
