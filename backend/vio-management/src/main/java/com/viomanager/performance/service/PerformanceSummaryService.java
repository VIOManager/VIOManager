package com.viomanager.performance.service;

import com.viomanager.performance.vo.MyPerfSummaryVO;
import com.viomanager.performance.vo.SummaryRowVO;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 绩效汇总服务（PF05 我的汇总 / PF09 管理端汇总 / PF10 导出，接口清单 §3.2）。
 * 汇总由打分事务内重算，无独立写接口；学生端"本周绩效"卡片 = PF05，部门管理员"本部门绩效" = PF09 total。
 */
public interface PerformanceSummaryService {

    /** PF05 我的绩效汇总（当前学期，服务端取 ACTIVE 行，免参）。 */
    MyPerfSummaryVO mySummary();

    /** PF09 汇总列表（评优参考，按 totalScore 降序；部门管理员本子树）。 */
    List<SummaryRowVO> summaries(Long semesterId, Long subOrgId, String memberKeyword);

    /** PF10 导出 Excel：groupBy = MEMBER 按人聚合 / ORG 按子组织聚合。 */
    void export(Long semesterId, Long subOrgId, String groupBy, HttpServletResponse response);
}
