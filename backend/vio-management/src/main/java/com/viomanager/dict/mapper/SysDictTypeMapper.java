package com.viomanager.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.dict.entity.SysDictType;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典类型 Mapper（SY01-04，仅超管调用——未声明 @DataScope 的方法对超管放行）。
 */
@Mapper
public interface SysDictTypeMapper extends BaseMapper<SysDictType> {
}
