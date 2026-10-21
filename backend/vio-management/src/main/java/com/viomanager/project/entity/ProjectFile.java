package com.viomanager.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 项目文件关联实体，对应表 project_file。
 * 两段式：先走骨架 F01 上传拿 fileId（格式/大小/ClamAV 校验在文件服务），再建本关联；
 * 冗余 file_name/file_size/scan_status 免跨表查（知识库同款模式）。
 * 上传权限比 KB 宽：所有人与项目成员均可传（清单 PR26）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("project_file")
public class ProjectFile extends BaseEntity {

    /** 项目 ID */
    private Long projectId;

    /** 文件对象 ID（file_object 中已含 scan_status） */
    private Long fileId;

    /** 文件名冗余 */
    private String fileName;

    /** 文件大小冗余 */
    private Long fileSize;

    /** 文件分类：DOC 文档 / IMAGE 图片 / CODE 代码 / OTHER 其他 */
    private String fileCategory;

    /** 检测状态：CLEAN 通过 / INFECTED 命中 / UNAVAILABLE 服务不可用被拒 */
    private String scanStatus;

    /** 命中特征名（被拒时记录） */
    private String scanSignature;

    /** 上传人人员 ID */
    private Long uploadedBy;

    /** 上传人姓名冗余 */
    private String uploadedByName;

    /** 备注 */
    private String remark;
}
