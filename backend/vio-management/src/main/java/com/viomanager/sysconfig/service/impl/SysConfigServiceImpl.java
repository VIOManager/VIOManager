package com.viomanager.sysconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.sysconfig.dto.ConfigUpdateDTO;
import com.viomanager.sysconfig.entity.SysConfig;
import com.viomanager.sysconfig.mapper.SysConfigMapper;
import com.viomanager.sysconfig.service.ISysConfigService;
import com.viomanager.sysconfig.vo.SysConfigVO;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 系统参数实现（CF01-02）。
 */
@Service
public class SysConfigServiceImpl implements ISysConfigService {

    @Resource
    private SysConfigMapper sysConfigMapper;

    @Override
    public Result<List<SysConfigVO>> list() {
        List<SysConfig> configs = sysConfigMapper.selectList(
                new QueryWrapper<SysConfig>().orderByAsc("config_key"));
        List<SysConfigVO> list = configs.stream().map(c -> {
            SysConfigVO vo = new SysConfigVO();
            vo.setConfigKey(c.getConfigKey());
            vo.setConfigValue(c.getConfigValue());
            vo.setConfigName(c.getConfigName());
            vo.setEditable(c.getEditable() != null && c.getEditable() == 1);
            vo.setRemark(c.getRemark());
            return vo;
        }).toList();
        return Result.ok(list);
    }

    @Override
    public void update(String key, ConfigUpdateDTO dto) {
        SysConfig config = sysConfigMapper.selectOne(
                new QueryWrapper<SysConfig>().eq("config_key", key));
        if (config == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "系统参数不存在");
        }
        if (config.getEditable() == null || config.getEditable() != 1) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "该参数为只读，不允许界面修改");
        }
        config.setConfigValue(dto.getConfigValue());
        sysConfigMapper.updateById(config);
    }
}
