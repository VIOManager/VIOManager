package com.viomanager.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.ProjectErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.material.support.MemberIdSupport;
import com.viomanager.project.dto.TaskCreateDTO;
import com.viomanager.project.dto.TaskUpdateDTO;
import com.viomanager.project.entity.Project;
import com.viomanager.project.entity.ProjectTask;
import com.viomanager.project.mapper.ProjectMapper;
import com.viomanager.project.mapper.ProjectTaskMapper;
import com.viomanager.project.service.ProjectTaskService;
import com.viomanager.project.support.ProjectVisibilitySupport;
import com.viomanager.project.vo.TaskVO;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 项目任务实现（PR21–PR24）。
 * PR23 权限矩阵（2026-10-16 拍板：管理员权限最高）：
 *  - 所有人/管理员（超管或项目组织子树内部门管理员）：全字段可改，状态任意流转（置 DONE 回填 completed_at/by，离开 DONE 清空）
 *  - 负责人本人（其余人）：仅 status=DONE（完成打卡语义），改其他字段 7002
 *  - 其余可见者：7002
 */
@Slf4j
@Service
public class ProjectTaskServiceImpl implements ProjectTaskService {

    private static final Set<String> TASK_STATUSES = Set.of("TODO", "DOING", "DONE", "BLOCKED");
    private static final Set<String> PRIORITIES = Set.of("HIGH", "NORMAL", "LOW");

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectTaskMapper taskMapper;

    @Resource
    private ProjectVisibilitySupport visibilitySupport;

    @Resource
    private MemberIdSupport memberIdSupport;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public Result<List<TaskVO>> list(Long projectId, String status, Long assigneeMemberId) {
        CurrentUser user = UserHolder.getUser();
        Long viewerId = memberIdSupport.currentMemberId(user);
        visibilitySupport.requireVisibleProject(projectId, user, viewerId);
        QueryWrapper<ProjectTask> qw = new QueryWrapper<>();
        qw.eq("project_id", projectId)
                .eq(StringUtils.hasText(status), "status", status)
                .eq(assigneeMemberId != null, "assignee_member_id", assigneeMemberId)
                .orderByAsc("sort_no").orderByAsc("id");
        List<TaskVO> vos = taskMapper.selectList(qw).stream().map(this::toVO).toList();
        return Result.ok(vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<TaskVO> create(Long projectId, TaskCreateDTO dto) {
        Project project = requireOwnedProject(projectId);
        ProjectTask task = new ProjectTask();
        task.setProjectId(projectId);
        task.setTitle(dto.getTitle().trim());
        task.setDescription(dto.getDescription());
        if (dto.getAssigneeMemberId() != null) {
            //负责人须人员档案存在（不强制是项目成员——外部协作场景，清单未限定）
            task.setAssigneeMemberId(dto.getAssigneeMemberId());
            task.setAssigneeName(requireMemberName(dto.getAssigneeMemberId()));
        }
        task.setDueDate(dto.getDueDate());
        String priority = dto.getPriority() == null ? "NORMAL" : dto.getPriority();
        if (!PRIORITIES.contains(priority)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
        task.setPriority(priority);
        task.setStatus("TODO");
        task.setSortNo(dto.getSortNo() == null ? 0 : dto.getSortNo());
        taskMapper.insert(task);
        return Result.ok(toVO(task));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> update(Long projectId, Long taskId, TaskUpdateDTO dto) {
        CurrentUser user = UserHolder.getUser();
        Long memberId = memberIdSupport.currentMemberId(user);
        Project project = requireProject(projectId);
        ProjectTask task = requireTask(projectId, taskId);
        if (visibilitySupport.canManage(project, user, memberId)) {
            updateAsOwner(taskId, memberId, dto);
            return Result.ok();
        }
        //负责人本人：仅 status=DONE 的完成打卡；其他任何修改 7002
        if (memberId != null && memberId.equals(task.getAssigneeMemberId())) {
            if (dto.getStatus() == null || !"DONE".equals(dto.getStatus())
                    || dto.getTitle() != null || dto.getDescription() != null
                    || dto.getAssigneeMemberId() != null || dto.getDueDate() != null
                    || dto.getPriority() != null || dto.getSortNo() != null) {
                throw new BizException(ProjectErrorCode.NOT_OWNER);
            }
            jdbcTemplate.update(
                    "UPDATE project_task SET status = 'DONE', completed_at = NOW(), completed_by = ? "
                            + "WHERE id = ? AND status <> 'DONE'",
                    memberId, taskId);
            return Result.ok();
        }
        throw new BizException(ProjectErrorCode.NOT_OWNER);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(Long projectId, Long taskId) {
        requireOwnedProject(projectId);
        ProjectTask task = requireTask(projectId, taskId);
        //同步生成的任务也可删，删后不自动重建（节点 synced 已置 1，回溯按"已有任务"跳过——
        //清单 PR24 口径：删除是明确决定，不复活）
        taskMapper.deleteById(task.getId());
        return Result.ok();
    }

    // ---------------------------------------------------------------- 支撑方法

    /** 所有人全字段编辑：状态流转校验 + DONE 完成列回填/清空 */
    private void updateAsOwner(Long taskId, Long ownerMemberId, TaskUpdateDTO dto) {
        if (StringUtils.hasText(dto.getStatus()) && !TASK_STATUSES.contains(dto.getStatus())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
        if (StringUtils.hasText(dto.getPriority()) && !PRIORITIES.contains(dto.getPriority())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
        if (dto.getAssigneeMemberId() != null) {
            requireMemberName(dto.getAssigneeMemberId());
        }
        ProjectTask patch = new ProjectTask();
        patch.setId(taskId);
        patch.setTitle(StringUtils.hasText(dto.getTitle()) ? dto.getTitle().trim() : null);
        patch.setDescription(dto.getDescription());
        patch.setAssigneeMemberId(dto.getAssigneeMemberId());
        if (dto.getAssigneeMemberId() != null) {
            patch.setAssigneeName(requireMemberName(dto.getAssigneeMemberId()));
        }
        patch.setDueDate(dto.getDueDate());
        patch.setPriority(dto.getPriority());
        patch.setSortNo(dto.getSortNo());
        patch.setStatus(dto.getStatus());
        taskMapper.updateById(patch);
        //完成列语义：置 DONE 回填（completed_by=操作人人员口径）；离开 DONE 清空——
        //updateById 的 NOT_NULL 策略写不了 NULL，完成列用手写 SQL 收口
        if (StringUtils.hasText(dto.getStatus())) {
            if ("DONE".equals(dto.getStatus())) {
                jdbcTemplate.update(
                        "UPDATE project_task SET completed_at = NOW(), completed_by = ? "
                                + "WHERE id = ? AND (completed_at IS NULL OR completed_by IS NULL)",
                        ownerMemberId, taskId);
            } else {
                jdbcTemplate.update(
                        "UPDATE project_task SET completed_at = NULL, completed_by = NULL "
                                + "WHERE id = ?", taskId);
            }
        }
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ProjectErrorCode.NOT_FOUND);
        }
        return project;
    }

    /** 创建/删除门槛（7002，所有人 or 管理员）——收口在 ProjectVisibilitySupport */
    private Project requireOwnedProject(Long projectId) {
        CurrentUser user = UserHolder.getUser();
        Long memberId = memberIdSupport.currentMemberId(user);
        return visibilitySupport.requireManageableProject(projectId, user, memberId);
    }

    /** 任务存在且属于该项目（7012：任务不存在或不属于该项目） */
    private ProjectTask requireTask(Long projectId, Long taskId) {
        ProjectTask task = taskMapper.selectById(taskId);
        if (task == null || !projectId.equals(task.getProjectId())) {
            throw new BizException(ProjectErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    private String requireMemberName(Long memberId) {
        List<String> names = jdbcTemplate.queryForList(
                "SELECT name FROM member WHERE id = ? AND deleted = 0", String.class, memberId);
        if (names.isEmpty()) {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
        return names.get(0);
    }

    private TaskVO toVO(ProjectTask task) {
        TaskVO vo = new TaskVO();
        vo.setId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setAssigneeMemberId(task.getAssigneeMemberId());
        vo.setAssigneeName(task.getAssigneeName());
        vo.setDueDate(task.getDueDate());
        vo.setStatus(task.getStatus());
        vo.setPriority(task.getPriority());
        vo.setMilestoneNodeId(task.getMilestoneNodeId());
        vo.setSortNo(task.getSortNo());
        vo.setCompletedAt(task.getCompletedAt());
        return vo;
    }
}
