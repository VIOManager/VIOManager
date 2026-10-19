package com.viomanager.project.service;

import com.viomanager.common.result.Result;
import com.viomanager.project.dto.TaskCreateDTO;
import com.viomanager.project.dto.TaskUpdateDTO;
import com.viomanager.project.vo.TaskVO;
import java.util.List;

/**
 * 项目任务（PR21–PR24）：手工任务 CRUD + 状态更新。
 * 读口走项目可见性；创建/删除/全字段编辑仅所有人（7002）；
 * 负责人本人仅可将状态置 DONE。
 */
public interface ProjectTaskService {

    /** PR21 任务列表：项目可见者可看；筛选 status / assigneeMemberId，按 sort_no 排序 */
    Result<List<TaskVO>> list(Long projectId, String status, Long assigneeMemberId);

    /** PR22 创建手工任务（milestone_node_id 恒空）：仅所有人 */
    Result<TaskVO> create(Long projectId, TaskCreateDTO dto);

    /** PR23 编辑或更新状态：所有人全字段；负责人本人仅可置 DONE（回填 completed_at/by） */
    Result<Void> update(Long projectId, Long taskId, TaskUpdateDTO dto);

    /** PR24 删除任务：仅所有人；同步生成的任务也可删（删后不自动重建） */
    Result<Void> delete(Long projectId, Long taskId);
}
