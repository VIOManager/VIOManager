package com.viomanager.performance.service;

import com.viomanager.performance.dto.PerfRuleSaveDTO;
import com.viomanager.performance.vo.PerfRuleVO;
import java.util.List;

/**
 * 绩效规则服务（PF11/PF12，接口清单 §3.3）。子组织未配置任何 enabled 规则时无法打分（8046），规则先行。
 */
public interface PerformanceRuleService {

    /** PF11 规则列表：特定学期规则优先、回退默认（semesterId 空）。 */
    List<PerfRuleVO> list(Long subOrgId, Long semesterId);

    /** PF12 批量保存（upsert）：semesterId 空 = 子组织默认规则。 */
    void save(PerfRuleSaveDTO dto);
}
