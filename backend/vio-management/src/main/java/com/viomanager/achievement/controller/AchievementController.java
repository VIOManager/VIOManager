package com.viomanager.achievement.controller;

import com.viomanager.achievement.dto.AchievementCreateDTO;
import com.viomanager.achievement.dto.AchievementEvidenceDTO;
import com.viomanager.achievement.dto.AchievementQueryDTO;
import com.viomanager.achievement.dto.AchievementUpdateDTO;
import com.viomanager.achievement.service.AchievementService;
import com.viomanager.achievement.vo.AchievementDetailVO;
import com.viomanager.achievement.vo.AchievementVO;
import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成果接口（A01–A06），口径以 C 线接口清单 §4 为准。
 * A01/A02 登录即可（数据范围/成员资格在 service 判定）；写接口按角色门槛收口。
 * @Idempotent 暂不挂（前端未带 X-Idempotency-Key 头，挂了必 1001）；四个写接口已挂 @AuditLog 留痕。
 */
@RestController
@RequestMapping("/api/achievements")
public class AchievementController {

    @Resource
    private AchievementService achievementService;

    /** A01 成果列表：管理端全量；学生只看自己在册队伍的成果（无队伍返回空数组） */
    @GetMapping
    public Result<List<AchievementVO>> list(AchievementQueryDTO dto) {
        return achievementService.list(dto);
    }

    /** A02 成果详情：管理端或本队在册成员，附 viewerIsLeader 标识 */
    @GetMapping("/{id}")
    public Result<AchievementDetailVO> detail(@PathVariable("id") Long id) {
        return achievementService.detail(id);
    }

    /** A03 提交成果：学生走队长校验 + 审批流，管理员直接录入生效 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "ACHIEVEMENT_CREATE", label = "提交成果",
            summary = "'提交成果：' + #dto.name")
    @PostMapping
    public Result<AchievementVO> create(@RequestBody AchievementCreateDTO dto) {
        return achievementService.create(dto);
    }

    /** A04 编辑成果：只改 name / level / achievedAt / description 四字段 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "ACHIEVEMENT_UPDATE", label = "编辑成果",
            summary = "'编辑成果：' + #id")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @RequestBody AchievementUpdateDTO dto) {
        achievementService.update(id, dto);
        return Result.ok();
    }

    /** A05 删除成果：已通过的不可删；附件级联逻辑删除 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "ACHIEVEMENT_DELETE", label = "删除成果",
            summary = "'删除成果：' + #id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        achievementService.delete(id);
        return Result.ok();
    }

    /** A06 佐证上传/覆盖：仅当前队长（5007），单文件覆盖式并同步 achievement 两列 */
    @RequireRole("STUDENT")
    @AuditLog(bizType = "ACHIEVEMENT_EVIDENCE_UPLOAD", label = "上传佐证",
            summary = "'上传佐证：成果' + #id")
    @PostMapping("/{id}/evidence")
    public Result<Void> uploadEvidence(@PathVariable("id") Long id,
                                       @RequestBody AchievementEvidenceDTO dto) {
        achievementService.uploadEvidence(id, dto);
        return Result.ok();
    }
}
