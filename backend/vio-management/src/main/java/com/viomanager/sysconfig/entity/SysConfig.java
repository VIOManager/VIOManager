package com.viomanager.sysconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 系统参数实体（表 sys_config，标准基座尾列齐全）。
 * 参数由建库种子维护，不提供新增/删除（清单 CF 口径）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sys_config")
public class SysConfig extends BaseEntity {

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置说明 */
    private String configName;

    /** 是否允许界面修改：1 可改 0 只读 */
    private Integer editable;

    /** 备注 */
    private String remark;
}
