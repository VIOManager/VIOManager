package com.viomanager.semester.service;

import com.viomanager.semester.dto.SemesterSaveDTO;
import com.viomanager.semester.vo.SemesterVO;
import java.util.List;

/**
 * 学期管理服务（A 线契约 S01-S04，仅超管）。
 * 切换任务 S05-S08 在 SwitchTaskService（Step 3-4 交付）。
 */
public interface SemesterService {

    /** S01 学期列表（新在前） */
    List<SemesterVO> list();

    /** S02 创建：名称不可重复（3008），新建固定 PREPARING 非当前 */
    Long create(SemesterSaveDTO dto);

    /** S03 编辑基础信息：改名同样查重（3008）；status/isCurrent 不收 */
    void update(Long id, SemesterSaveDTO dto);

    /** S04 删除：有关联比赛/赛队/成果/切换任务拒绝（3009） */
    void delete(Long id);
}
