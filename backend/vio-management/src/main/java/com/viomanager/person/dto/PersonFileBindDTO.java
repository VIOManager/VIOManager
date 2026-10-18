package com.viomanager.person.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * M08/M09 证件照与电子签名回填入参。
 * 前端先走文件服务 F01 上传拿 fileId，再提交本结构回填档案——后端不收 MultipartFile，
 * 避免"业务接口二次上传"绕开文件服务的病毒扫描闸门。
 */
@Data
public class PersonFileBindDTO {

    /** 文件服务返回的文件 ID（须为当前登录人上传、扩展名 jpg/jpeg/png、已过病毒扫描） */
    @NotNull(message = "fileId 不能为空")
    private Long fileId;
}
