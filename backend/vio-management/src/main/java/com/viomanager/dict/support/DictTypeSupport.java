package com.viomanager.dict.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.dict.dto.DictTypeCreateDTO;
import com.viomanager.dict.dto.DictTypeUpdateDTO;
import com.viomanager.dict.entity.SysDictType;
import com.viomanager.dict.mapper.SysDictItemMapper;
import com.viomanager.dict.mapper.SysDictTypeMapper;
import com.viomanager.dict.vo.DictTypeVO;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 字典类型管理（SY01-04，超管独占）。
 * 类型就几个，条目计数走一次 GROUP BY（SysDictItemMapper.countByCode）免 N+1。
 */
@Component
public class DictTypeSupport {

    /** 预设类型（建库种子，清单 §2）不可删；新预设入库时在此登记 */
    public static final Set<String> PRESET_CODES = Set.of("SKILL", "MATERIAL_TYPE", "KB_CATEGORY");

    @Resource
    private SysDictTypeMapper sysDictTypeMapper;

    @Resource
    private SysDictItemMapper sysDictItemMapper;

    /** SY01 类型列表：dictCode dictName remark 条目数 待审核数 preset */
    public List<DictTypeVO> listTypes() {
        List<SysDictType> types = sysDictTypeMapper.selectList(
                new QueryWrapper<SysDictType>().orderByAsc("dict_code"));
        Map<String, Map<String, Object>> counts = new HashMap<>();
        for (Map<String, Object> row : sysDictItemMapper.countByCode()) {
            counts.put(String.valueOf(row.get("dictCode")), row);
        }
        return types.stream().map(t -> {
            DictTypeVO vo = new DictTypeVO();
            vo.setId(t.getId());
            vo.setDictCode(t.getDictCode());
            vo.setDictName(t.getDictName());
            vo.setRemark(t.getRemark());
            Map<String, Object> cnt = counts.get(t.getDictCode());
            vo.setItemCount(cnt == null ? 0 : ((Number) cnt.get("itemCount")).intValue());
            vo.setPendingCount(cnt == null ? 0 : ((Number) cnt.get("pendingCount")).intValue());
            vo.setPreset(PRESET_CODES.contains(t.getDictCode()));
            return vo;
        }).toList();
    }

    /** SY02 新建类型：编码重复 → 1001 */
    public Long createType(DictTypeCreateDTO dto) {
        Long dup = sysDictTypeMapper.selectCount(
                new QueryWrapper<SysDictType>().eq("dict_code", dto.getDictCode()));
        if (dup > 0) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "字典编码已存在");
        }
        SysDictType type = new SysDictType();
        type.setDictCode(dto.getDictCode());
        type.setDictName(dto.getDictName());
        type.setRemark(dto.getRemark());
        sysDictTypeMapper.insert(type);
        return type.getId();
    }

    /** SY03 编辑名称/说明（dictCode 创建后不可改） */
    public void updateType(Long id, DictTypeUpdateDTO dto) {
        SysDictType type = requireType(id);
        type.setDictName(dto.getDictName());
        type.setRemark(dto.getRemark());
        sysDictTypeMapper.updateById(type);
    }

    /** SY04 删除：预设类型或仍有条目 → 1006（先清空条目再删自定义类型） */
    public void deleteType(Long id) {
        SysDictType type = requireType(id);
        if (PRESET_CODES.contains(type.getDictCode())) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "预设类型不可删除");
        }
        Long items = sysDictItemMapper.selectCount(
                new QueryWrapper<com.viomanager.dict.entity.SysDictItem>()
                        .eq("dict_code", type.getDictCode()));
        if (items > 0) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "该类型下仍有条目，须先清空再删除");
        }
        sysDictTypeMapper.deleteById(id);
    }

    private SysDictType requireType(Long id) {
        SysDictType type = sysDictTypeMapper.selectById(id);
        if (type == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "字典类型不存在");
        }
        return type;
    }
}
