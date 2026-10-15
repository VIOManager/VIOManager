package com.viomanager.project.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.result.PageResult;
import com.viomanager.project.dto.ProjectQueryDTO;
import com.viomanager.project.entity.Project;
import com.viomanager.project.mapper.ProjectMapper;
import com.viomanager.project.vo.ProjectDetailVO;
import com.viomanager.project.vo.ProjectVO;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 项目读口（PR01 列表 / PR02 创建返回 / PR03 详情装配）。
 * 数据范围按角色自动（清单 PR01）；跨线名（org/semester/contest）批量 IN 装配，列表零 JOIN。
 */
@Component
public class ProjectQuerySupport {

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectVisibilitySupport visibilitySupport;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** PR01 列表：数据范围（超管全部/部门管理员子树/学生参与+授权）+ 筛选 + 分页 */
    public PageResult<ProjectVO> list(ProjectQueryDTO dto, CurrentUser user, Long memberId) {
        int page = dto.getPage() == null || dto.getPage() < 1 ? 1 : dto.getPage();
        int size = dto.getSize() == null || dto.getSize() < 1 ? 20 : Math.min(dto.getSize(), 100);

        QueryWrapper<Project> qw = new QueryWrapper<>();
        //1.数据范围
        if ("SUPER_ADMIN".equals(user.getRole())) {
            //超管全部
        } else if ("DEPT_ADMIN".equals(user.getRole())) {
            //部门管理员：本部门子树（orgId 为空时查不到任何行，从严处理）
            qw.in("org_id", subtreeOrgIds(user.getOrgId()));
        } else {
            //学生：参与的 ∪ 授权可见的（不含 PUBLIC/ORG 全量，清单 PR01 口径）
            List<Long> ids = visibilitySupport.studentVisibleIds(memberId);
            if (ids.isEmpty()) {
                return PageResult.of(List.of(), 0, page, size);
            }
            qw.in("id", ids);
        }
        //2.筛选
        qw.like(StringUtils.hasText(dto.getKeyword()), "name", dto.getKeyword())
                .eq(StringUtils.hasText(dto.getStatus()), "status", dto.getStatus())
                .eq(dto.getContestId() != null, "contest_id", dto.getContestId())
                .eq(dto.getSemesterId() != null, "semester_id", dto.getSemesterId())
                .eq(dto.getOrgId() != null, "org_id", dto.getOrgId())
                .orderByDesc("updated_at");

        Page<Project> result = projectMapper.selectPage(new Page<>(page, size), qw);
        List<ProjectVO> vos = toCards(result.getRecords());
        return PageResult.of(vos, result.getTotal(), page, size);
    }

    /** 详情装配（可见性判定已由调用方经 requireVisibleProject 完成） */
    public ProjectDetailVO toDetail(Project project) {
        ProjectDetailVO vo = new ProjectDetailVO();
        fillCard(vo, project, nameMaps(List.of(project)));
        vo.setDescription(project.getDescription());
        vo.setRemark(project.getRemark());
        return vo;
    }

    /** 批量装配列表卡片（跨线名一次 IN 取齐，列表零 JOIN；PR15 挂载池等同款复用） */
    public List<ProjectVO> toCards(List<Project> projects) {
        NameMaps names = nameMaps(projects);
        return projects.stream().map(p -> {
            ProjectVO vo = new ProjectVO();
            fillCard(vo, p, names);
            return vo;
        }).toList();
    }

    private void fillCard(ProjectVO vo, Project project, NameMaps names) {
        vo.setId(project.getId());
        vo.setName(project.getName());
        vo.setOwnerName(project.getOwnerName());
        vo.setOrgName(names.orgs.get(project.getOrgId()));
        vo.setSemesterName(names.semesters.get(project.getSemesterId()));
        vo.setContestName(names.contests.get(project.getContestId()));
        vo.setStatus(project.getStatus());
        vo.setVisibility(project.getVisibility());
        vo.setStartDate(project.getStartDate());
        vo.setEndDate(project.getEndDate());
        vo.setCreatedAt(project.getCreatedAt());
        vo.setUpdatedAt(project.getUpdatedAt());
    }

    /** 跨线名一次取齐（集合化，方案 §7.8）：org/semester/contest 各一条 IN 查询 */
    private NameMaps nameMaps(List<Project> projects) {
        NameMaps names = new NameMaps();
        names.orgs = queryNames("sys_org",
                projects.stream().map(Project::getOrgId).collect(Collectors.toSet()));
        names.semesters = queryNames("semester",
                projects.stream().map(Project::getSemesterId).collect(Collectors.toSet()));
        names.contests = queryNames("contest",
                projects.stream().map(Project::getContestId).filter(id -> id != null)
                        .collect(Collectors.toSet()));
        return names;
    }

    private Map<Long, String> queryNames(String table, java.util.Set<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        String inClause = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        //RowCallbackHandler 逐行回调（rs 已定位在当前行），直接读列即可——别写 while(rs.next())；
        //显式转型消歧：单表达式 lambda 同时兼容 ResultSetExtractor 重载
        jdbcTemplate.query(
                "SELECT id, name FROM " + table + " WHERE id IN (" + inClause + ")",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        result.put(rs.getLong("id"), rs.getString("name")));
        return result;
    }

    /** 管理员子树组织 ID 集（含自身；orgId 为空返回空集=从严查不到） */
    private List<Long> subtreeOrgIds(Long adminOrgId) {
        if (adminOrgId == null) {
            return List.of();
        }
        List<String> paths = jdbcTemplate.queryForList(
                "SELECT path FROM sys_org WHERE id = ? AND deleted = 0", String.class, adminOrgId);
        if (paths.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                "SELECT id FROM sys_org WHERE deleted = 0 AND path LIKE CONCAT(?, '%')",
                Long.class, paths.get(0));
    }

    /** 跨线名三件套（一次装配期内复用） */
    private static class NameMaps {
        Map<Long, String> orgs = new HashMap<>();
        Map<Long, String> semesters = new HashMap<>();
        Map<Long, String> contests = new HashMap<>();
    }
}
