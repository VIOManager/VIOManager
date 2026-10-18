package com.viomanager.dict.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.dict.dto.DictItemAuditDTO;
import com.viomanager.dict.dto.DictItemCreateDTO;
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

    /** D02 用户补充：值在字典内唯一（含未审核与已驳回，防止换个马甲重复提交） */
    @Override
    public Long createSkillItem(String dictCode, DictItemCreateDTO dto) {
        //查重走声明了 @DataScope(ALL) 的 mapper 方法——D02 登录即可提交，学生不能被默认拒绝拦下
        if (sysDictItemMapper.countByValue(dictCode, dto.getItemValue()) > 0) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "该技能值已存在（含待审核与已驳回项）");
        }
        //1.排序号取当前最大+1，新补充项排在预设项之后
        int maxSort = sysDictItemMapper.maxSortNo(dictCode);
        //2.落库：来源用户补充、待审核、启用
        SysDictItem item = new SysDictItem();
        item.setDictCode(dictCode);
        item.setItemValue(dto.getItemValue());
        item.setItemLabel(dto.getItemLabel());
        item.setSource("USER");
        item.setAuditStatus("PENDING");
        item.setSortNo(maxSort + 1);
        item.setStatus(1);
        sysDictItemMapper.insert(item);
        return item.getId();
    }

    /** D03 审核：仅 PENDING 可审（条件更新防重复审核/并发） */
    @Override
    public void auditItem(Long id, DictItemAuditDTO dto) {
        SysDictItem item = sysDictItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "字典项不存在");
        }
        int rows = sysDictItemMapper.update(null, new UpdateWrapper<SysDictItem>()
                .eq("id", id).eq("audit_status", "PENDING")
                .set("audit_status", Boolean.TRUE.equals(dto.getApprove()) ? "APPROVED" : "REJECTED"));
        if (rows == 0) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "仅待审核状态的字典项可审核");
        }
    }
}
