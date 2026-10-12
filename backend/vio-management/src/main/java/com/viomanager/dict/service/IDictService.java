package com.viomanager.dict.service;

import com.viomanager.common.result.Result;
import com.viomanager.dict.vo.DictItemVO;
import java.util.List;

/**
 * 字典服务：前端枚举下拉的统一数据源（骨架接口清单 D01）。
 */
public interface IDictService {

    /**
     * 按字典编码读字典项列表（PRESET + 用户补充，带审核状态）。
     * 登录即可调；编码不存在返回空数组（下拉框场景宁空不报错）。
     */
    Result<List<DictItemVO>> listItems(String dictCode);
}
