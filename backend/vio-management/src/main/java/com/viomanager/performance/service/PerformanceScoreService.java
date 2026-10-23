package com.viomanager.performance.service;

import com.viomanager.performance.dto.WeeklyScoreDTO;

/**
 * 绩效打分服务（PF07/PF08，接口清单 §3.2）。**不走审批中心**——管理员直审，打分动作即审核（PRD §3.13）。
 */
public interface PerformanceScoreService {

    /**
     * PF07 打分：维度集合与该子组织 enabled 规则完全一致（8043）、不超满分（8044）、
     * 未配置规则拒绝（8046）；快照 weight/max_score 落库；事务内重算 summary 与子组织内排名。
     */
    void score(Long id, WeeklyScoreDTO dto);

    /** PF08 驳回：reason 必填，置 REJECTED（不删记录，PF03 改后重进待审核）。 */
    void reject(Long id, String reason);
}
