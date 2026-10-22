package com.viomanager.performance.service;

import com.viomanager.performance.dto.WeeklyResubmitDTO;
import com.viomanager.performance.dto.WeeklySubmitDTO;
import com.viomanager.performance.vo.WeeklyDetailVO;
import com.viomanager.performance.vo.WeeklyItemVO;
import java.util.List;

/**
 * 绩效周报服务（PF01–PF04 学生侧 + PF06 管理侧列表，接口清单 §3）。
 * 同人同周同子组织仅一份周报（唯一键兜底）；REJECTED 走 PF03 改后重进待审核。
 */
public interface PerformanceWeeklyService {

    /** PF01 我的周报列表（semesterId 默认当前学期）。 */
    List<WeeklyItemVO> myWeeklies(Long semesterId, String status);

    /** PF02 提交周报：subOrgId 服务端取本人所属组织；同周已有 SUBMITTED/SCORED 拒绝（8041）。返回周报 ID。 */
    Long submit(WeeklySubmitDTO dto);

    /** PF03 重新提交：仅本人（8045）且 REJECTED（8042），改内容后回 SUBMITTED。 */
    void resubmit(Long id, WeeklyResubmitDTO dto);

    /** PF04 周报详情：本人或管理员（部门管理员本子树）；SCORED 后含维度明细与评语。 */
    WeeklyDetailVO detail(Long id);

    /** PF06 周报列表（审核工作台）：管理端按子树裁剪，可按周次/子组织/状态/姓名关键词筛选。 */
    List<WeeklyItemVO> adminList(Long semesterId, Integer weekNo, Long subOrgId, String status, String memberKeyword);
}
