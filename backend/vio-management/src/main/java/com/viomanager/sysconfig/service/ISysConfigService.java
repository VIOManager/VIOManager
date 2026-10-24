package com.viomanager.sysconfig.service;

import com.viomanager.common.result.Result;
import com.viomanager.sysconfig.dto.ConfigUpdateDTO;
import com.viomanager.sysconfig.vo.SysConfigVO;
import java.util.List;

/**
 * 系统参数服务（CF01-02，超管独占）。参数由建库种子维护，不提供新增/删除。
 */
public interface ISysConfigService {

    /** CF01 参数列表：configKey configValue configName editable remark */
    Result<List<SysConfigVO>> list();

    /** CF02 修改参数值：editable=0 → 1006；key 不存在 → 1004 */
    void update(String key, ConfigUpdateDTO dto);
}
