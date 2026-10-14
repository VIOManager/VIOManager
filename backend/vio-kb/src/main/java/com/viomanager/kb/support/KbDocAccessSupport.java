package com.viomanager.kb.support;

import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.KbDocStatus;
import com.viomanager.common.enums.KbErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.kb.entity.KbDoc;
import com.viomanager.kb.mapper.KbDocMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 文档可见性判定（本模块口径单点维护，六个读写口共用，绝不各写一遍）。
 * 浏览侧：拍板口径"PUBLISHED 全员可见（登录即可），不按组织隔离"——
 * 不存在与不可见同一文案 9002，不给按 ID 枚举试探空间（对齐 PRD 防越权要求）。
 * 管理侧：任意状态可见，不存在回 1004。
 */
@Component
public class KbDocAccessSupport {

    @Resource
    private KbDocMapper kbDocMapper;

    /** 浏览侧门槛：必须存在且 PUBLISHED（KB02/KB05/KB08/KB09 共用） */
    public KbDoc requirePublished(Long id) {
        KbDoc doc = kbDocMapper.selectById(id);
        if (doc == null || !KbDocStatus.PUBLISHED.name().equals(doc.getStatus())) {
            throw new BizException(KbErrorCode.KB_DOC_NOT_AVAILABLE);
        }
        return doc;
    }

    /** 管理侧门槛：必须存在，状态不限（KB13–KB17 共用） */
    public KbDoc requireExists(Long id) {
        KbDoc doc = kbDocMapper.selectById(id);
        if (doc == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }
}
