package com.viomanager.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.ProjectErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.material.support.MemberIdSupport;
import com.viomanager.project.dto.ProjectCreateDTO;
import com.viomanager.project.dto.ProjectQueryDTO;
import com.viomanager.project.dto.ProjectUpdateDTO;
import com.viomanager.project.entity.Project;
import com.viomanager.project.entity.ProjectAdvisor;
import com.viomanager.project.entity.ProjectFile;
import com.viomanager.project.entity.ProjectMember;
import com.viomanager.project.entity.ProjectTask;
import com.viomanager.project.entity.ProjectViewGrant;
import com.viomanager.project.entity.TeamProjectRef;
import com.viomanager.project.mapper.ProjectAdvisorMapper;
import com.viomanager.project.mapper.ProjectFileMapper;
import com.viomanager.project.mapper.ProjectMapper;
import com.viomanager.project.mapper.ProjectMemberMapper;
import com.viomanager.project.mapper.ProjectTaskMapper;
import com.viomanager.project.mapper.ProjectViewGrantMapper;
import com.viomanager.project.mapper.TeamProjectRefMapper;
import com.viomanager.project.service.ProjectService;
import com.viomanager.project.support.ProjectQuerySupport;
import com.viomanager.project.support.ProjectVisibilitySupport;
import com.viomanager.project.vo.ProjectDetailVO;
import com.viomanager.project.vo.ProjectVO;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 项目主体实现（PR01–PR05）。
 * 并发口径（方案 §7.7）：
 *  - 名称唯一（7004）靠 uk_project_name 兜底，撞键转业务码不回放
 *  - 状态流转用条件 UPDATE（casStatus，WHERE status=旧值），并发跳档不可能
 *  - 编辑冲突用 updatedAt 乐观锁（1007），前端编辑页回显值带回
 */
@Slf4j
@Service
public class ProjectServiceImpl implements ProjectService {

    /** visibility 合法值（DDL 注释口径：PRIVATE 仅所有人 / ORG 本部门 / PUBLIC 全平台） */
    private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "ORG", "PUBLIC");

    /** 状态流转顺序（清单 PR04：限 ACTIVE→FINISHED→ARCHIVED 按序，不允许回退与跳档） */
    private static final Map<String, String> NEXT_STATUS =
            Map.of("ACTIVE", "FINISHED", "FINISHED", "ARCHIVED");

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectMemberMapper memberMapper;

    @Resource
    private ProjectAdvisorMapper advisorMapper;

    @Resource
    private TeamProjectRefMapper refMapper;

    @Resource
    private ProjectTaskMapper taskMapper;

    @Resource
    private ProjectFileMapper fileMapper;

    @Resource
    private ProjectViewGrantMapper grantMapper;

    @Resource
    private ProjectVisibilitySupport visibilitySupport;

    @Resource
    private ProjectQuerySupport querySupport;

    @Resource
    private MemberIdSupport memberIdSupport;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public Result<PageResult<ProjectVO>> list(ProjectQueryDTO dto) {
        CurrentUser user = UserHolder.getUser();
        Long memberId = memberIdSupport.currentMemberId(user);
        return Result.ok(querySupport.list(dto, user, memberId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<ProjectDetailVO> create(ProjectCreateDTO dto) {
        CurrentUser user = UserHolder.getUser();
        //所有人=当前用户：项目是个人资产，必须绑定人员档案（admin 超管账号无档案，报 7013 明确提示而非参数错）
        Long ownerId = memberIdSupport.currentMemberId(user);
        if (ownerId == null) {
            throw new BizException(ProjectErrorCode.NO_MEMBER_PROFILE);
        }

        Project project = new Project();
        project.setName(dto.getName().trim());
        project.setDescription(dto.getDescription());
        project.setOwnerMemberId(ownerId);
        project.setOwnerName(user.getRealName());
        project.setContestId(dto.getContestId());
        project.setTrackId(dto.getTrackId());
        //学期：显式传入 > 当前激活学期；semester_id NOT NULL，缺失拦参数错
        Long semesterId = dto.getSemesterId() != null ? dto.getSemesterId() : currentSemesterId();
        if (semesterId == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
        project.setSemesterId(semesterId);
        //组织：显式传入 > 本人组织；org_id NOT NULL（数据范围与绩效隔离依据）
        Long orgId = dto.getOrgId() != null ? dto.getOrgId() : user.getOrgId();
        if (orgId == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
        project.setOrgId(orgId);
        //可见范围：默认 ORG（同部门可见），非法值拦参数错
        String visibility = dto.getVisibility() == null ? "ORG" : dto.getVisibility();
        if (!VISIBILITIES.contains(visibility)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
        project.setVisibility(visibility);
        project.setStatus("ACTIVE");
        project.setStartDate(dto.getStartDate());
        project.setEndDate(dto.getEndDate());
        project.setRemark(dto.getRemark());

        try {
            projectMapper.insert(project);
        } catch (DuplicateKeyException e) {
            //uk_project_name(semester_id, org_id, name, deleted) 撞键：同学期同组织重名
            throw new BizException(ProjectErrorCode.NAME_EXISTS);
        }
        //建项目即在成员表落 OWNER 行：PR06 列表含角色的锚点、PR08"OWNER 行不可移除"的前提；
        //组织冗余取用户所属组织（非项目组织，显式跨组织建项目时两者不同）
        ProjectMember ownerRow = new ProjectMember();
        ownerRow.setProjectId(project.getId());
        ownerRow.setMemberId(ownerId);
        ownerRow.setMemberName(user.getRealName());
        ownerRow.setMemberOrgId(user.getOrgId());
        ownerRow.setProjectRole("OWNER");
        memberMapper.insert(ownerRow);
        return Result.ok(querySupport.toDetail(project));
    }

    @Override
    public Result<ProjectDetailVO> detail(Long id) {
        CurrentUser user = UserHolder.getUser();
        Long memberId = memberIdSupport.currentMemberId(user);
        //PR03 专用入口：靠授权（第 7 条）可见时消耗 ONCE（用完置 EXPIRED）；其余读口不消耗
        Project project = visibilitySupport.requireVisibleProjectConsumingOnce(id, user, memberId);
        ProjectDetailVO vo = querySupport.toDetail(project);
        vo.setViewerIsOwner(memberId != null && memberId.equals(project.getOwnerMemberId()));
        return Result.ok(vo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> update(Long id, ProjectUpdateDTO dto) {
        CurrentUser user = UserHolder.getUser();
        Long memberId = memberIdSupport.currentMemberId(user);
        Project project = requireProject(id);
        requireOwner(project, memberId);
        //乐观锁：编辑页回显的 updatedAt 与当前值不一致=别人已改过（1007）
        if (dto.getUpdatedAt() != null && !dto.getUpdatedAt().equals(project.getUpdatedAt())) {
            throw new BizException(CommonErrorCode.CONCURRENT_MODIFIED);
        }

        //状态流转：只允许按序走下一步（1006），条件 UPDATE 防并发跳档（0 行=并发改过，1007）
        if (StringUtils.hasText(dto.getStatus()) && !dto.getStatus().equals(project.getStatus())) {
            String expectedNext = NEXT_STATUS.get(project.getStatus());
            if (expectedNext == null || !expectedNext.equals(dto.getStatus())) {
                throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED);
            }
            int rows = projectMapper.casStatus(id, project.getStatus(), dto.getStatus(), user.getUserId());
            if (rows == 0) {
                throw new BizException(CommonErrorCode.CONCURRENT_MODIFIED);
            }
        }

        //基本字段：null 不更新（patch 语义），status 列由上面的 CAS 独占管理
        Project patch = new Project();
        patch.setId(id);
        if (StringUtils.hasText(dto.getName())) {
            patch.setName(dto.getName().trim());
        }
        patch.setDescription(dto.getDescription());
        patch.setContestId(dto.getContestId());
        patch.setTrackId(dto.getTrackId());
        patch.setVisibility(StringUtils.hasText(dto.getVisibility())
                && VISIBILITIES.contains(dto.getVisibility()) ? dto.getVisibility() : null);
        patch.setStartDate(dto.getStartDate());
        patch.setEndDate(dto.getEndDate());
        patch.setRemark(dto.getRemark());
        if (patch.getName() != null || patch.getDescription() != null || patch.getContestId() != null
                || patch.getTrackId() != null || patch.getVisibility() != null
                || patch.getStartDate() != null || patch.getEndDate() != null
                || patch.getRemark() != null) {
            try {
                projectMapper.updateById(patch);
            } catch (DuplicateKeyException e) {
                //改名撞上同学期同组织已有名称
                throw new BizException(ProjectErrorCode.NAME_EXISTS);
            }
        }
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(Long id) {
        CurrentUser user = UserHolder.getUser();
        Long memberId = memberIdSupport.currentMemberId(user);
        Project project = requireProject(id);
        requireOwner(project, memberId);
        //有 ACTIVE 挂载的赛队先解绑（7010）——RELEASSED 历史行不阻碍删除
        Integer activeMounts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_project_ref WHERE project_id = ? AND status = 'ACTIVE' AND deleted = 0",
                Integer.class, id);
        if (activeMounts != null && activeMounts > 0) {
            throw new BizException(ProjectErrorCode.HAS_ACTIVE_MOUNT);
        }
        //逻辑删主体 + 子表随项目逻辑删（清单 PR05：任务/文件/授权；成员/教师/挂载同口径一并清理）
        projectMapper.deleteById(id);
        memberMapper.delete(new QueryWrapper<ProjectMember>().eq("project_id", id));
        advisorMapper.delete(new QueryWrapper<ProjectAdvisor>().eq("project_id", id));
        refMapper.delete(new QueryWrapper<TeamProjectRef>().eq("project_id", id));
        taskMapper.delete(new QueryWrapper<ProjectTask>().eq("project_id", id));
        fileMapper.delete(new QueryWrapper<ProjectFile>().eq("project_id", id));
        grantMapper.delete(new QueryWrapper<ProjectViewGrant>().eq("project_id", id));
        return Result.ok();
    }

    /** 加载项目（7001 不存在）——写口专用，不做可见性判定（写口一律仅所有人） */
    private Project requireProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BizException(ProjectErrorCode.NOT_FOUND);
        }
        return project;
    }

    /** 写口所有人校验（7002）：memberId 为空（admin 无档案）必然不是所有人 */
    private void requireOwner(Project project, Long memberId) {
        if (memberId == null || !memberId.equals(project.getOwnerMemberId())) {
            throw new BizException(ProjectErrorCode.NOT_OWNER);
        }
    }

    /** 当前激活学期 ID（A 线 semester.is_current=1），未配置返回 null 由调用方拦截 */
    private Long currentSemesterId() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM semester WHERE is_current = 1 AND deleted = 0 LIMIT 1", Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
