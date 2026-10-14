package com.viomanager.schedule.service;

import com.viomanager.schedule.dto.ScheduleRuleSaveDTO;
import com.viomanager.schedule.entity.ScheduleRule;
import com.viomanager.schedule.vo.ScheduleRuleVO;
import java.util.List;
import java.util.Map;

/**
 * 排班规则服务（SC10/SC11，接口清单 §5）。
 * 规则匹配：优先「子组织+学期」特定规则，无则回退默认（semester_id 为空）；未配置时段视为不启用（8007）。
 */
public interface ScheduleRuleService {

    /** SC10 规则列表：7×5 全时段视图（未配置时段按未启用返回）。 */
    List<ScheduleRuleVO> list(Long subOrgId, Long semesterId);

    /** SC11 批量保存（upsert）：semesterId 空 = 子组织默认规则。 */
    void save(ScheduleRuleSaveDTO dto);

    /**
     * 生效规则视图（校验链用）：键 "weekday-slotIndex" → 生效规则实体（特定学期覆盖默认）。
     * SC02/SC03 校验链的 8007/8006 判定与 SC10 展示同源。
     */
    Map<String, ScheduleRule> effectiveRuleMap(Long subOrgId, Long semesterId);
}
