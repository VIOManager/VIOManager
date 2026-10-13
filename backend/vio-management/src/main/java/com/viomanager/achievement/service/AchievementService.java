package com.viomanager.achievement.service;

import com.viomanager.achievement.dto.AchievementCreateDTO;
import com.viomanager.achievement.dto.AchievementEvidenceDTO;
import com.viomanager.achievement.dto.AchievementQueryDTO;
import com.viomanager.achievement.dto.AchievementUpdateDTO;
import com.viomanager.achievement.vo.AchievementDetailVO;
import com.viomanager.achievement.vo.AchievementVO;
import com.viomanager.achievement.vo.MyAchievementVO;
import com.viomanager.common.result.Result;
import java.util.List;

/**
 * 成果服务（A01–A06 + A08）：列表 / 详情 / 提交 / 编辑 / 删除 / 佐证上传 / 学生端我的成果，
 * 口径以 C 线接口清单 §4 为准。
 */
public interface AchievementService {

    /** 成果列表（A01）：管理端全量；学生只看自己在册队伍的成果（无队伍返回空数组） */
    Result<List<AchievementVO>> list(AchievementQueryDTO dto);

    /** 成果详情（A02）：管理端或本队在册成员可看，附队长视角标识 */
    Result<AchievementDetailVO> detail(Long id);

    /** 提交成果（A03）：学生走队长校验 + 审批流，管理员直接录入生效（分支见 AchievementApprovalSupport） */
    Result<AchievementVO> create(AchievementCreateDTO dto);

    /** 编辑成果（A04）：只改 name / level / achievedAt / description 四字段 */
    void update(Long id, AchievementUpdateDTO dto);

    /** 删除成果（A05）：已通过的不可删；级联逻辑删除 achievement_file 附件 */
    void delete(Long id);

    /** 佐证上传/覆盖（A06）：仅当前队长（5007）；单文件覆盖式并同步 achievement 两列 */
    void uploadEvidence(Long id, AchievementEvidenceDTO dto);

    /** 学生端我的成果（A08）：参赛记录 + 获奖记录从成员关系与成果动态聚合 */
    Result<MyAchievementVO> myContributions();
}
