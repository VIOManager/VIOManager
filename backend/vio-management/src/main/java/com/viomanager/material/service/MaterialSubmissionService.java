package com.viomanager.material.service;

import com.viomanager.common.result.Result;
import com.viomanager.material.dto.MaterialSubmitDTO;
import com.viomanager.material.vo.MaterialSubmitVO;

/**
 * 材料提交服务（MS02）：单份提交 / 覆盖上传，提交成功后触发齐全率重算。
 */
public interface MaterialSubmissionService {

    /** 单份提交 / 覆盖上传（MS02）：8 步校验 → 同事务失效旧版 + 插新版本，返回新版本号 */
    Result<MaterialSubmitVO> submit(Long requirementId, MaterialSubmitDTO dto);
}
