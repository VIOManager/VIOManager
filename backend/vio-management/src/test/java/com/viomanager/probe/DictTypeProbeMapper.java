package com.viomanager.probe;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 仅测试用：sys_dict_type 的 BaseMapper，验证 MyBatis-Plus 基座行为。
 * 业务模块的正式 mapper 照此模式：放各自模块的 mapper 包并标 @Mapper。
 */
@Mapper
public interface DictTypeProbeMapper extends BaseMapper<SysDictTypeProbeEntity> {
}
