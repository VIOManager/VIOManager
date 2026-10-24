package com.viomanager.dict.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.dict.dto.DictItemAuditAdminDTO;
import com.viomanager.dict.dto.DictItemAuditDTO;
import com.viomanager.dict.dto.DictItemCreateDTO;
import com.viomanager.dict.dto.DictItemStatusDTO;
import com.viomanager.dict.dto.DictItemUpdateDTO;
import com.viomanager.dict.dto.DictTypeCreateDTO;
import com.viomanager.dict.dto.DictTypeUpdateDTO;
import com.viomanager.dict.entity.SysDictItem;
import com.viomanager.dict.mapper.SysDictItemMapper;
import com.viomanager.dict.service.IDictService;
import com.viomanager.dict.support.DictItemAdminSupport;
import com.viomanager.dict.support.DictTypeSupport;
import com.viomanager.dict.vo.DictItemAdminVO;
import com.viomanager.dict.vo.DictItemVO;
import com.viomanager.dict.vo.DictTypeVO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 字典服务实现：D01-D03 薄编排 + SY01-10 委托两个 support（类型/条目管理）。
 * 本期直查库（表有索引、量小，方案 Step 7 拍板，不引缓存）。
 */
@Service
public class DictServiceImpl implements IDictService {

    @Resource
    private SysDictItemMapper sysDictItemMapper;

    @Resource
    private DictTypeSupport dictTypeSupport;

    @Resource
    private DictItemAdminSupport dictItemAdminSupport;

    @Override
    public Result<List<DictItemVO>> listItems(String dictCode) {
        //1.查启用中的字典项（PRESET 与用户补充都在，审核状态不筛，带回给前端自行处理）
        List<SysDictItem> items = sysDictItemMapper.selectEnabledByCode(dictCode);
        //2.实体转 VO（口径字段：itemValue itemLabel source auditStatus sortNo + id/label 别名）
        List<DictItemVO> voList = new ArrayList<>();
        for (SysDictItem item : items) {
            DictItemVO vo = BeanUtil.copyProperties(item, DictItemVO.class);
            vo.setLabel(item.getItemLabel());
            voList.add(vo);
        }
        //3.补充人姓名仅管理员可见（学生下拉用不到，也不为学生查用户表——数据范围拦截器口径）
        fillCreatedByNames(voList, items);
        //4.编码不存在时 items 为空，返回空数组——下拉框场景宁空不报错
        return Result.ok(voList);
    }

    /** D02 + SY06 统一新增（角色分流见 DictItemAdminSupport） */
    @Override
    public Long createItem(String dictCode, DictItemCreateDTO dto) {
        return dictItemAdminSupport.createItem(dictCode, dto);
    }

    /** D03 审核（POST 形态）：approve/pass 别名解析 + 仅 PENDING 可审（条件更新防并发） */
    @Override
    public void auditItem(Long id, DictItemAuditDTO dto) {
        Boolean approve = dto.getApprove() != null ? dto.getApprove() : dto.getPass();
        if (approve == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "审核结论不能为空");
        }
        SysDictItem item = sysDictItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "字典项不存在");
        }
        int rows = sysDictItemMapper.update(null, new UpdateWrapper<SysDictItem>()
                .eq("id", id).eq("audit_status", "PENDING")
                .set("audit_status", Boolean.TRUE.equals(approve) ? "APPROVED" : "REJECTED"));
        if (rows == 0) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "仅待审核状态的字典项可审核");
        }
    }

    // ---------- SY01-10：委托 support ----------

    @Override
    public Result<List<DictTypeVO>> listTypes() {
        return Result.ok(dictTypeSupport.listTypes());
    }

    @Override
    public Long createType(DictTypeCreateDTO dto) {
        return dictTypeSupport.createType(dto);
    }

    @Override
    public void updateType(Long id, DictTypeUpdateDTO dto) {
        dictTypeSupport.updateType(id, dto);
    }

    @Override
    public void deleteType(Long id) {
        dictTypeSupport.deleteType(id);
    }

    @Override
    public Result<List<DictItemAdminVO>> adminItems(String code, String source, String status,
                                                    String auditStatus, String keyword) {
        return Result.ok(dictItemAdminSupport.adminItems(code, source, status, auditStatus, keyword));
    }

    @Override
    public void updateItem(Long id, DictItemUpdateDTO dto) {
        dictItemAdminSupport.updateItem(id, dto);
    }

    @Override
    public void deleteItem(Long id) {
        dictItemAdminSupport.deleteItem(id);
    }

    @Override
    public void toggleItem(Long id, DictItemStatusDTO dto) {
        dictItemAdminSupport.toggleItem(id, dto);
    }

    @Override
    public void auditItemAdmin(Long id, DictItemAuditAdminDTO dto) {
        dictItemAdminSupport.auditItemAdmin(id, dto);
    }

    // ---------- 私有支撑 ----------

    private void fillCreatedByNames(List<DictItemVO> voList, List<SysDictItem> items) {
        CurrentUser user = UserHolder.getUser();
        if (user == null || !("SUPER_ADMIN".equals(user.getRole()) || "DEPT_ADMIN".equals(user.getRole()))) {
            return;
        }
        List<Long> userIds = items.stream().map(SysDictItem::getCreatedBy)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = dictItemAdminSupport.userNames(userIds);
        for (int i = 0; i < items.size(); i++) {
            Long by = items.get(i).getCreatedBy();
            if (by != null) {
                voList.get(i).setCreatedByName(names.get(by));
            }
        }
    }
}
