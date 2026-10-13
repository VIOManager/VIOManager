package com.viomanager.team.controller;

import com.viomanager.common.result.Result;
import com.viomanager.team.service.ITeamQueryService;
import com.viomanager.team.vo.TeamDetailVO;
import com.viomanager.team.vo.TeamListItemVO;
import com.viomanager.team.vo.TeamMemberSearchVO;
import com.viomanager.team.vo.TeamMemberVO;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 赛队查询接口（TE01 列表 / TE02 详情 / TE05 搜索 / TE11 成员列表）。
 * 可见性与字段裁剪在 service 层强制：学生仅自己所在队伍；详情按 viewerRole 裁剪敏感字段。
 * 无 @RequireRole：数据范围在 service 按 UserHolder 角色 + sys_org.path 子树判定。
 */
@RestController
@RequestMapping("/api/teams")
public class TeamQueryController {

    @Resource
    private ITeamQueryService queryService;

    /** TE01 列表：超管全部 / 部门管理员本组织子树 / 学生仅自己所在队伍 */
    @GetMapping
    public Result<List<TeamListItemVO>> list(
            @RequestParam(value = "contestId", required = false) Long contestId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return queryService.listTeams(contestId, keyword);
    }

    /** TE02 详情：非本队成员 4008；返回 viewerRole 供前端按钮显隐 */
    @GetMapping("/{id}")
    public Result<TeamDetailVO> detail(@PathVariable("id") Long id) {
        return queryService.detail(id);
    }

    /** TE05 加人搜索：姓名模糊匹配 member，标 inTeam；须本队成员/管理端可调 */
    @GetMapping("/{id}/members/search")
    public Result<List<TeamMemberSearchVO>> searchMembers(
            @PathVariable("id") Long id,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return queryService.searchMembers(id, keyword);
    }

    /** TE11 成员列表：按 viewerRole 裁剪敏感字段 */
    @GetMapping("/{id}/members")
    public Result<List<TeamMemberVO>> listMembers(@PathVariable("id") Long id) {
        return queryService.listMembers(id);
    }
}
