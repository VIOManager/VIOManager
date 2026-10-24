package com.viomanager.dict.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.dict.dto.DictItemAuditAdminDTO;
import com.viomanager.dict.dto.DictItemCreateDTO;
import com.viomanager.dict.dto.DictItemStatusDTO;
import com.viomanager.dict.dto.DictItemUpdateDTO;
import com.viomanager.dict.entity.SysDictItem;
import com.viomanager.dict.mapper.SysDictItemMapper;
import com.viomanager.dict.vo.DictItemAdminVO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 字典条目管理（SY05-10 + D02/SY06 统一新增）。
 * 一个新增口按角色分流：超管 = SY06 预置直接生效；其他角色 = D02 用户补充（仅 SKILL、待审核）。
 */
@Component
public class DictItemAdminSupport {

    /** 条目列表显式列（组长 SQL 规矩：点名防未来加宽列被带出） */
    private static final String[] ITEM_COLUMNS = {
            "id", "dict_code", "item_value", "item_label", "source", "audit_status",
            "sort_no", "status", "created_by"};

    @Resource
    private SysDictItemMapper sysDictItemMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    /** SY05 管理视角条目列表（含停用/待审核项）。筛选：source status auditStatus keyword */
    public List<DictItemAdminVO> adminItems(String code, String source, String status,
                                            String auditStatus, String keyword) {
        QueryWrapper<SysDictItem> qw = new QueryWrapper<>();
        qw.select(ITEM_COLUMNS);
        qw.eq("dict_code", code);
        qw.eq(notBlank(source), "source", source);
        if (notBlank(status)) {
            qw.eq("status", "ENABLED".equals(status) ? 1 : 0);
        }
        qw.eq(notBlank(auditStatus), "audit_status", auditStatus);
        if (notBlank(keyword)) {
            qw.and(w -> w.like("item_value", keyword).or().like("item_label", keyword));
        }
        qw.orderByAsc("sort_no").orderByAsc("id");
        List<SysDictItem> items = sysDictItemMapper.selectList(qw);
        Map<Long, String> names = userNames(collectCreatedBy(items));
        List<DictItemAdminVO> list = new ArrayList<>(items.size());
        for (SysDictItem item : items) {
            DictItemAdminVO vo = new DictItemAdminVO();
            vo.setId(item.getId());
            vo.setItemValue(item.getItemValue());
            vo.setItemLabel(item.getItemLabel());
            vo.setSortNo(item.getSortNo());
            vo.setSource(item.getSource());
            vo.setStatus(item.getStatus() == 1 ? "ENABLED" : "DISABLED");
            vo.setAuditStatus(item.getAuditStatus());
            //空 Map 用 Map.of()（不可变，get(null) 会 NPE——直插数据无 createdBy），先判空再取
            Long by = item.getCreatedBy();
            vo.setCreatedByName(by == null ? null : names.get(by));
            list.add(vo);
        }
        return list;
    }

    /**
     * D02 + SY06 统一新增。别名字段解析：系统配置页发 itemValue/itemLabel，
     * A 线面板只发 label（值缺省取显示名）。
     */
    public Long createItem(String code, DictItemCreateDTO dto) {
        String label = firstNonBlank(dto.getItemLabel(), dto.getLabel());
        String value = firstNonBlank(dto.getItemValue(), label);
        if (label == null || value == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "条目值与显示名不能为空");
        }
        //值在字典内唯一（含待审核与已驳回，防换马甲重复提交）
        if (sysDictItemMapper.countByValue(code, value) > 0) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "该字典值已存在（含待审核与已驳回项）");
        }
        SysDictItem item = new SysDictItem();
        item.setDictCode(code);
        item.setItemValue(value);
        item.setItemLabel(label);
        item.setStatus(1);
        if (isSuperAdmin()) {
            //SY06 超管预置：直接生效
            item.setSource("PRESET");
            item.setAuditStatus("APPROVED");
            item.setSortNo(dto.getSortNo() != null ? dto.getSortNo() : sysDictItemMapper.maxSortNo(code) + 1);
        } else {
            //D02 用户补充：仅开放 SKILL（清单拍板），入库待审核
            if (!"SKILL".equals(code)) {
                throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "用户补充词条仅开放技能字典（SKILL）");
            }
            item.setSource("USER");
            item.setAuditStatus("PENDING");
            item.setSortNo(sysDictItemMapper.maxSortNo(code) + 1);
        }
        sysDictItemMapper.insert(item);
        return item.getId();
    }

    /** SY07 编辑显示名/排序 */
    public void updateItem(Long id, DictItemUpdateDTO dto) {
        SysDictItem item = requireItem(id);
        item.setItemLabel(dto.getItemLabel());
        if (dto.getSortNo() != null) {
            item.setSortNo(dto.getSortNo());
        }
        sysDictItemMapper.updateById(item);
    }

    /** SY08 逻辑删除（历史数据 value 原文兜底，停用优先于删除） */
    public void deleteItem(Long id) {
        requireItem(id);
        sysDictItemMapper.deleteById(id);
    }

    /** SY09 启用/停用：停用项不再出现在 DR01，历史数据不受影响 */
    public void toggleItem(Long id, DictItemStatusDTO dto) {
        SysDictItem item = requireItem(id);
        item.setStatus("ENABLED".equals(dto.getStatus()) ? 1 : 0);
        sysDictItemMapper.updateById(item);
    }

    /** SY10 审核用户补充词条：非 USER/PENDING → 1006；条件更新防并发重复审核 */
    public void auditItemAdmin(Long id, DictItemAuditAdminDTO dto) {
        SysDictItem item = requireItem(id);
        if (!"USER".equals(item.getSource()) || !"PENDING".equals(item.getAuditStatus())) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "仅用户补充的待审核词条可审核");
        }
        int rows = sysDictItemMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SysDictItem>()
                        .eq("id", id).eq("audit_status", "PENDING")
                        .set("audit_status", dto.getAuditStatus()));
        if (rows == 0) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "该词条已被审核，勿重复操作");
        }
    }

    /** 用户 id → 姓名批量查（管理员链路专用：学生调未声明范围的 mapper 会被默认拒绝） */
    public Map<Long, String> userNames(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
            names.put(user.getId(), user.getRealName());
        }
        return names;
    }

    private SysDictItem requireItem(Long id) {
        SysDictItem item = sysDictItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "字典项不存在");
        }
        return item;
    }

    private Collection<Long> collectCreatedBy(List<SysDictItem> items) {
        List<Long> ids = new ArrayList<>();
        for (SysDictItem item : items) {
            if (item.getCreatedBy() != null) {
                ids.add(item.getCreatedBy());
            }
        }
        return ids;
    }

    private boolean isSuperAdmin() {
        CurrentUser user = UserHolder.getUser();
        return user != null && "SUPER_ADMIN".equals(user.getRole());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
