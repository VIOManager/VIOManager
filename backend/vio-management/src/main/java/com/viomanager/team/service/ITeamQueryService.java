package com.viomanager.team.service;

import com.viomanager.common.result.Result;
import com.viomanager.team.vo.TeamDetailVO;
import com.viomanager.team.vo.TeamListItemVO;
import com.viomanager.team.vo.TeamMemberSearchVO;
import com.viomanager.team.vo.TeamMemberVO;
import java.util.List;

/**
 * 赛队查询服务（TE01 列表 / TE02 详情 / TE05 搜索 / TE11 成员列表）。
 * 可见性与字段裁剪在后端强制：学生仅本队；详情按 viewerRole 裁剪敏感字段。
 */
public interface ITeamQueryService {

    /** TE01 列表：超管全部 / 部门管理员本组织子树 / 学生仅自己所在队伍。返回数组（非分页）。 */
    Result<List<TeamListItemVO>> listTeams(Long contestId, String keyword);

    /** TE02 详情：非本队成员 4008；返回 viewerRole 供前端按钮显隐。 */
    Result<TeamDetailVO> detail(Long teamId);

    /** TE05 加人搜索：姓名模糊匹配 member，标 inTeam；须本队成员/管理端可调。 */
    Result<List<TeamMemberSearchVO>> searchMembers(Long teamId, String keyword);

    /** TE11 成员列表：按 viewerRole 裁剪敏感字段。 */
    Result<List<TeamMemberVO>> listMembers(Long teamId);
}
