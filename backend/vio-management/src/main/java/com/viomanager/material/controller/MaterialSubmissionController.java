package com.viomanager.material.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import com.viomanager.material.dto.MaterialBatchUploadDTO;
import com.viomanager.material.dto.MaterialSubmitDTO;
import com.viomanager.material.service.MaterialSubmissionService;
import com.viomanager.material.vo.BatchMatchResultVO;
import com.viomanager.material.vo.MaterialSubmitVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 材料提交接口（MS02 单份 / MS03 zip 批量）：队长或队员均可提交（PRD §3.7），管理员也可代传——
 * 在册成员校验（5003）对所有角色一视同仁。
 * 校验链与覆盖上传语义见 MaterialSubmissionServiceImpl，批量匹配说明见 MaterialZipBatchSupport；
 * @Idempotent 暂不挂（前端未带 X-Idempotency-Key 头，挂了必 1001）；两个写接口已挂 @AuditLog 留痕。
 */
@RestController
@RequestMapping("/api/materials")
public class MaterialSubmissionController {

    @Resource
    private MaterialSubmissionService submissionService;

    /** MS02 单份提交/覆盖上传：同一要求重复提交即覆盖旧版（is_current 翻 0，版本号 +1），返回新版本号 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "MATERIAL_SUBMIT", label = "提交材料",
            summary = "'提交材料：要求' + #requirementId")
    @PostMapping("/requirements/{requirementId}/submit")
    public Result<MaterialSubmitVO> submit(@PathVariable("requirementId") Long requirementId,
                                           @RequestBody MaterialSubmitDTO dto) {
        return submissionService.submit(requirementId, dto);
    }

    /** MS03 zip 批量上传：压缩包按类型中文名匹配到各要求，已交过的跳过；返回匹配与未匹配清单 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "MATERIAL_SUBMIT_BATCH", label = "zip批量提交材料",
            summary = "'zip批量提交：任务' + #dto.taskId")
    @PostMapping("/submissions/batch")
    public Result<BatchMatchResultVO> batchUpload(@RequestBody MaterialBatchUploadDTO dto) {
        return submissionService.batchUpload(dto);
    }
}
