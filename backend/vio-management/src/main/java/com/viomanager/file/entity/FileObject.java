package com.viomanager.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 文件对象实体，对应表 file_object。
 * 一行 = 一个已落 MinIO 的文件；scan_status 是它能否被下载的闸门（只有 CLEAN 放行）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("file_object")
public class FileObject extends BaseEntity {

    /** MinIO 对象 key，形如 material/2026/10/09/uuid.pdf */
    private String objectKey;

    /** 原始文件名，下载时回显 */
    private String originalName;

    /** 扩展名（小写，不含点） */
    private String fileExt;

    /** MIME 类型 */
    private String contentType;

    /** 字节数 */
    private Long fileSize;

    /** 内容 SHA-256，用于秒传与去重（本步只存档，秒传逻辑后续按需实现） */
    private String sha256;

    /** 业务类型：MEMBER_PHOTO / MEMBER_SIGNATURE / MATERIAL / ACHIEVEMENT / PROJECT */
    private String bizType;

    /** 关联业务 ID，可空（先上传后关联的场景） */
    private Long bizId;

    /** 病毒检测：PENDING 待检 / CLEAN 通过 / INFECTED 命中 / SKIPPED 未启用 / UNAVAILABLE 扫描服务不可用 */
    private String scanStatus;

    /** 命中的病毒特征名 */
    private String scanSignature;

    /** 检测时间 */
    private LocalDateTime scanAt;

    /** 累计下载次数 */
    private Integer downloadCount;

    /** 上传人所属组织，供数据范围过滤 */
    private Long orgId;
}
