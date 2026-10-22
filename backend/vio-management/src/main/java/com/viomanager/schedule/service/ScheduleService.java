package com.viomanager.schedule.service;

import com.viomanager.schedule.dto.ScheduleBatchCreateDTO;
import com.viomanager.schedule.dto.ScheduleUpdateDTO;
import com.viomanager.schedule.vo.MyScheduleVO;
import com.viomanager.schedule.vo.ScheduleCellVO;
import java.util.List;

/**
 * 排班服务（SC01–SC05，接口清单 §4）。
 * 校验链（SC02/SC03 按序）：8009 非本子组织 → 8007 时段未启用 → 8006 超上限
 * → 8005 同周同人同子组织仅一条 → 8008 课表冲突（force 强制保存打标记）。
 */
public interface ScheduleService {

    /** SC01 周排班表：7×5 格子视图；超管全部、部门管理员本子树。 */
    List<ScheduleCellVO> weekTable(Long semesterId, Integer weekNo, Long subOrgId);

    /** SC02 批量排班（事务内逐条过校验链，任一条不满足整体回滚）。 */
    void createBatch(ScheduleBatchCreateDTO dto);

    /** SC03 编辑单条（改时段/值班内容/备注），改时段后重新过校验链（无 force，冲突即拒绝）。 */
    void update(Long id, ScheduleUpdateDTO dto);

    /** SC04 取消排班（置 CANCELLED，幂等）。 */
    void cancel(Long id);

    /** SC05 我的排班（学生视角，member_id = 当前人；管理端隐藏此菜单）。 */
    List<MyScheduleVO> my(Long semesterId, Integer weekNo);
}
