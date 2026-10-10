package com.viomanager.dict.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.viomanager.common.result.Result;
import com.viomanager.dict.entity.SysDictItem;
import com.viomanager.dict.mapper.SysDictItemMapper;
import com.viomanager.dict.service.IDictService;
import com.viomanager.dict.vo.DictItemVO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 字典服务实现：本期直查库（表有索引、量小，方案 Step 7 拍板，不引缓存）。
 */
@Service
public class DictServiceImpl implements IDictService {

    @Resource
    private SysDictItemMapper sysDictItemMapper;

    @Override
    public Result<List<DictItemVO>> listItems(String dictCode) {
        //1.查启用中的字典项（PRESET 与用户补充都在，审核状态不筛，带回给前端自行处理）
        List<SysDictItem> items = sysDictItemMapper.selectEnabledByCode(dictCode);
        //2.实体转 VO（口径字段：itemValue itemLabel source auditStatus sortNo）
        List<DictItemVO> voList = new ArrayList<>();
        for (SysDictItem item : items) {
            voList.add(BeanUtil.copyProperties(item, DictItemVO.class));
        }
        //3.编码不存在时 items 为空，返回空数组——下拉框场景宁空不报错
        return Result.ok(voList);
    }
}
