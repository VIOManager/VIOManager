package com.viomanager.achievement.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 成果附件实体，对应表 achievement_file。
 * 字段与 V1__init_52_tables.sql 第 506–523 行一一对应。
 *
 * ⚠️ 版本模型与 material_submission 相同：同一成果多个附件版本靠 is_current / version_no 区分。
 * ⚠️ 本表**无 org_id**：数据范围过滤必须 join achievement 表（接口清单 A05「级联清理附件关联」）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("achievement_file")
public class AchievementFile extends BaseEntity {

    /** 成果 ID */
    private Long achievementId;

    /** 文件对象 ID */
    private Long fileId;

    /** 文件名冗余 */
    private String fileName;

    /** 附件类型：EVIDENCE 佐证材料 / ANNEX 附加材料 */
    private String fileType;

    /** 是否当前生效版本：1 生效 0 已被覆盖 */
    @TableField("is_current")
    private Integer isCurrent;

    /** 版本号 */
    @TableField("version_no")
    private Integer versionNo;

    /** 上传时间 */
    private LocalDateTime uploadedAt;
}
