package com.viomanager.team.service;

import java.math.BigDecimal;

/**
 * 跨线回写契约（B 线 team 包提供，C 线材料模块调用）：
 * 材料提交/覆盖上传后重算出的队伍材料状态与齐全率，经此口子写回 team 表。
 * C 线禁止直接 UPDATE team 表——team 表归赛队逻辑管，外人只经此口写 material_status 两列。
 */
public interface TeamMaterialStatusService {

    /**
     * 更新赛队材料状态与齐全率。
     *
     * @param teamId         赛队 ID
     * @param materialStatus 材料状态：NOT_STARTED / PENDING / SUBMITTED（材料成果清单 §5 联动口径）
     * @param completeRate   齐全率（0-100 保留 1 位小数）
     */
    void updateMaterialStatus(Long teamId, String materialStatus, BigDecimal completeRate);
}
