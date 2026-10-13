package com.viomanager.material.controller;

import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import com.viomanager.material.dto.MaterialSubmitDTO;
import com.viomanager.material.service.MaterialSubmissionService;
import com.viomanager.material.vo.MaterialSubmitVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 材料提交接口（MS02）：队长或队员均可提交（PRD §3.7），管理员也可代传——
 * 在册成员校验（5003）对所有角色一视同仁。
 * 8 步校验链与覆盖上传语义见 MaterialSubmissionServiceImpl；
 * @Idempotent / @AuditLog 待 bizType 词表在规范登记后补挂。
 */
@RestController
@RequestMapping("/api/materials")
public class MaterialSubmissionController {

    @Resource
    private MaterialSubmissionService submissionService;

    /** MS02 单份提交/覆盖上传：同一要求重复提交即覆盖旧版（is_current 翻 0，版本号 +1），返回新版本号 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @PostMapping("/requirements/{requirementId}/submit")
    public Result<MaterialSubmitVO> submit(@PathVariable("requirementId") Long requirementId,
                                           @RequestBody MaterialSubmitDTO dto) {
        return submissionService.submit(requirementId, dto);
    }
}
