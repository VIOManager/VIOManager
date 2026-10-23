package com.viomanager.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 课表导入批次实体，对应表 timetable_import（V1__init_52_tables.sql 第 801–823 行，表结构即契约）。
 * 状态机：PENDING 待归一化 → MAPPING 待确认映射 → COMPLETED 全部确认；解析全部失败 → FAILED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("timetable_import")
public class TimetableImport extends BaseEntity {

    /** 归一化后关联的学期 ID；归一化失败时为空，待 TT05 人工指定 */
    private Long semesterId;

    /** 导入文件中的原始学期名称 */
    private String semesterRawName;

    /** 原始课表文件 ID（file_object.id） */
    private Long fileId;

    /** 原始文件名 */
    private String fileName;

    /** 上传操作人所属组织 */
    private Long orgId;

    /** 总行数 */
    private Integer totalRows;

    /** 成功行数 */
    private Integer successRows;

    /** 失败行数 */
    private Integer failedRows;

    /** 每行错误原因 JSON（[{rowNo,reason}]，TT04 详情直出） */
    private String errorDetail;

    /** 状态：PENDING / MAPPING / COMPLETED / FAILED */
    private String status;

    /** 导入人 sys_user.id */
    private Long importedBy;

    /** 导入时间 */
    private LocalDateTime importedAt;
}
