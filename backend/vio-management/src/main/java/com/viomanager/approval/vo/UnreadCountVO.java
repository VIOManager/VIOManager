package com.viomanager.approval.vo;

import lombok.Data;

/**
 * 未读数出参（P08）：两类红点。
 */
@Data
public class UnreadCountVO {

    /** 待处理数：发给我的邀请 / 解散通知（TARGET 视角 PENDING 且未读） */
    private long todoCount;

    /** 结果数：我发起的申请被处理了但我还没看（applicant_read=0 且状态非 PENDING） */
    private long resultCount;
}
