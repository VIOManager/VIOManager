package com.viomanager.project.service;

import com.viomanager.common.result.Result;
import com.viomanager.project.dto.ProjectAdvisorAddDTO;
import com.viomanager.project.dto.ProjectMemberAddDTO;
import com.viomanager.project.vo.ProjectMemberVO;
import java.util.List;

/**
 * 项目成员与指导教师（PR06–PR10）：加人挂名都不走审批（PRD §3.9 审批清单不含项目加人）。
 * 写口一律仅所有人（7002）；读口走项目可见性判定。
 */
public interface ProjectMemberService {

    /** PR06 成员列表：含角色与加入时间；项目可见者可看 */
    Result<List<ProjectMemberVO>> listMembers(Long projectId);

    /** PR07 添加成员：仅所有人；已在项目 7005；目标人员不存在 1004 */
    Result<Void> addMember(Long projectId, ProjectMemberAddDTO dto);

    /** PR08 移除成员：仅所有人；OWNER 行不可移除（7002） */
    Result<Void> removeMember(Long projectId, Long memberId);

    /** PR09 添加指导教师：仅所有人；目标须教师类型（7006，不存在同码从严） */
    Result<Void> addAdvisor(Long projectId, ProjectAdvisorAddDTO dto);

    /** PR10 移除指导教师：仅所有人 */
    Result<Void> removeAdvisor(Long projectId, Long memberId);
}
