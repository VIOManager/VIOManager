package com.viomanager.sysconfig.vo;

import lombok.Data;

/**
 * CF01 系统参数列表项。editable 出参用布尔（前端契约），库内是 1/0。
 */
@Data
public class SysConfigVO {

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置说明 */
    private String configName;

    /** 是否允许界面修改 */
    private Boolean editable;

    /** 备注 */
    private String remark;
}
