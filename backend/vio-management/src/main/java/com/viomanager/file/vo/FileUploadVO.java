package com.viomanager.file.vo;

import lombok.Data;

/**
 * 上传成功出参，字段口径 = 骨架接口清单 F01：fileId scanStatus originalName。
 */
@Data
public class FileUploadVO {

    /** 文件 ID（全局序列化为字符串，前端拿它调下载接口） */
    private Long fileId;

    /** 病毒检测结果：CLEAN 通过 / INFECTED 命中 / UNAVAILABLE 扫描服务不可用 */
    private String scanStatus;

    /** 原始文件名 */
    private String originalName;
}
