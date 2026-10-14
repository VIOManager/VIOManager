package com.viomanager.schedule.service;

import com.viomanager.schedule.vo.ClassMappingVO;
import com.viomanager.schedule.vo.CourseRecordVO;
import java.util.List;

/**
 * 班级映射与课表查询服务（TT06–TT08，接口清单 §2）。
 * 未确认班级永远不参与空课统计（PRD §3.11 铁规则）。
 */
public interface ClassMappingService {

    /** TT06 映射列表：按学期、确认状态筛选。 */
    List<ClassMappingVO> list(Long semesterId, Boolean confirmed);

    /** TT07 批量确认映射：确认后该班级参与空课统计；任一 id 无效 8004。 */
    void confirm(List<Long> ids);

    /** TT08 课表记录查询（管理端核对）：semesterId 必填，weekNo / classKey 可选。 */
    List<CourseRecordVO> courses(Long semesterId, Integer weekNo, String classKey);
}
