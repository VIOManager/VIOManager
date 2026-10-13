package com.viomanager.material.service;

import com.viomanager.common.result.Result;
import com.viomanager.material.dto.MaterialBatchUploadDTO;
import com.viomanager.material.dto.MaterialSubmitDTO;
import com.viomanager.material.vo.BatchMatchResultVO;
import com.viomanager.material.vo.MaterialSubmitVO;

/**
 * 材料提交服务（MS02 单份提交 / MS03 zip 批量上传），提交成功后触发齐全率重算。
 */
public interface MaterialSubmissionService {

    /** 单份提交 / 覆盖上传（MS02）：8 步校验 → 同事务失效旧版 + 插新版本，返回新版本号 */
    Result<MaterialSubmitVO> submit(Long requirementId, MaterialSubmitDTO dto);

    /** zip 批量上传（MS03）：6 步校验 → 两层匹配落库（已交跳过）→ 齐全率重算，返回匹配结果 */
    Result<BatchMatchResultVO> batchUpload(MaterialBatchUploadDTO dto);
}
