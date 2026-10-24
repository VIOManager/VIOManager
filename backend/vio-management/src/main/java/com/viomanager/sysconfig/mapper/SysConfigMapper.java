package com.viomanager.sysconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.sysconfig.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统参数 Mapper（CF01-02，仅超管调用——未声明 @DataScope 的方法对超管放行）。
 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {
}
