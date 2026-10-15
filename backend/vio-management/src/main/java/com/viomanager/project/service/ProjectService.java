package com.viomanager.project.service;

import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.project.dto.ProjectCreateDTO;
import com.viomanager.project.dto.ProjectQueryDTO;
import com.viomanager.project.dto.ProjectUpdateDTO;
import com.viomanager.project.vo.ProjectDetailVO;
import com.viomanager.project.vo.ProjectVO;

/**
 * 项目主体（PR01–PR05）：个人资产的增删改查与状态流转。
 * 读口可见性判定唯一收口在 ProjectVisibilitySupport；写口一律仅所有人（7002）。
 */
public interface ProjectService {

    /** PR01 列表：数据范围按角色自动（超管全部/部门管理员子树/学生参与+授权） */
    Result<PageResult<ProjectVO>> list(ProjectQueryDTO dto);

    /** PR02 创建：所有人=当前用户；同学期同组织名称唯一（7004） */
    Result<ProjectDetailVO> create(ProjectCreateDTO dto);

    /** PR03 详情：可见性八条规则判定（7003）；ONCE 授权消耗随 M6 授权落地一并实现 */
    Result<ProjectDetailVO> detail(Long id);

    /** PR04 编辑：仅所有人（7002）；乐观锁（1007）；状态限 ACTIVE→FINISHED→ARCHIVED 按序 */
    Result<Void> update(Long id, ProjectUpdateDTO dto);

    /** PR05 删除：仅所有人；有 ACTIVE 挂载拒绝（7010）；子表随项目逻辑删 */
    Result<Void> delete(Long id);
}
