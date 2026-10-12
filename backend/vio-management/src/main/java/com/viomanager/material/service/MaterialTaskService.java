package com.viomanager.material.service;

import com.viomanager.common.result.Result;
import com.viomanager.material.dto.MaterialTaskCreateDTO;
import com.viomanager.material.dto.MaterialTaskUpdateDTO;
import com.viomanager.material.vo.MaterialTaskVO;
import java.util.List;

/**
 * 材料任务服务（MT01–MT04）：任务列表 / 创建 / 编辑 / 删除，管理端配置接口，
 * 口径以 C 线接口清单 §2 为准。
 */
public interface MaterialTaskService {

    /** 任务列表（MT01）：contestId 传了按比赛筛选，不传返回全部（均只含未删任务） */
    Result<List<MaterialTaskVO>> list(Long contestId);

    /** 创建任务（MT02）：同一比赛仅一个任务（重复抛 1001）；semesterId 随比赛带出 */
    Result<MaterialTaskVO> create(MaterialTaskCreateDTO dto);

    /** 编辑任务（MT03）：只改 name / description / deadline 三字段（PUT 全量替换） */
    void update(Long id, MaterialTaskUpdateDTO dto);

    /** 删除任务（MT04）：任一要求已有提交记录抛 5006 拒绝；级联逻辑删除任务下要求 */
    void delete(Long id);
}
