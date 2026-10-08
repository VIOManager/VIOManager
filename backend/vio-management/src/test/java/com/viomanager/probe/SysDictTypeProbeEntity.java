package com.viomanager.probe;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;

/**
 * 仅测试用：映射真实表 sys_dict_type，验证 BaseEntity 的审计填充与逻辑删除。
 * 位于 src/test，不进入运行时；Step 7 字典模块的正式实体另行编写。
 */
@TableName("sys_dict_type")
public class SysDictTypeProbeEntity extends BaseEntity {

    private String dictCode;

    private String dictName;

    private String remark;

    public String getDictCode() {
        return dictCode;
    }

    public void setDictCode(String dictCode) {
        this.dictCode = dictCode;
    }

    public String getDictName() {
        return dictName;
    }

    public void setDictName(String dictName) {
        this.dictName = dictName;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
