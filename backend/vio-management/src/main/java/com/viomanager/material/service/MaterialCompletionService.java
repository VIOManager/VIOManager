package com.viomanager.material.service;

/**
 * 队伍材料齐全率重算服务：MS02 提交 / 覆盖成功后在同一事务内调用，
 * 重算并回写 team 表的 material_status 与 material_complete_rate 两列。
 */
public interface MaterialCompletionService {

    /**
     * 重算某队的齐全率与材料状态。幂等收敛动作，不做强校验——
     * 队伍 / 材料任务 / 要求任一不存在时静默返回，不抛异常。
     */
    void recompute(Long teamId);
}
