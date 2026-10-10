package com.viomanager.dict.controller;

import com.viomanager.common.result.Result;
import com.viomanager.dict.service.IDictService;
import com.viomanager.dict.vo.DictItemVO;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典接口：通用字典读。接口口径以骨架接口清单 D01 为准。
 * 登录即可调（无角色门槛）；只读接口不挂幂等与审计。
 */
@RestController
@RequestMapping("/api/dicts")
public class DictController {

    @Resource
    private IDictService dictService;

    /**
     * 通用字典读：前端枚举下拉统一走它。
     * 例：GET /api/dicts/SKILL 返回技能字典（预设项 + 用户补充项，带审核状态）。
     */
    @GetMapping("/{code}")
    public Result<List<DictItemVO>> listItems(@PathVariable("code") String code) {
        return dictService.listItems(code);
    }
}
