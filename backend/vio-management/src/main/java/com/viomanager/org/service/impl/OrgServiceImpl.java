package com.viomanager.org.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.OrgErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.datascop.OrgScopeSupport;
import com.viomanager.org.dto.OrgSaveDTO;
import com.viomanager.org.entity.SysOrg;
import com.viomanager.org.mapper.SysOrgMapper;
import com.viomanager.org.service.OrgService;
import com.viomanager.org.support.CollegeCatalog;
import com.viomanager.org.vo.CollegeVO;
import com.viomanager.org.vo.OrgDetailVO;
import com.viomanager.org.vo.OrgNodeVO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组织管理实现（A 线契约 O01-O06）。
 * 数据范围口径：超管全平台；部门管理员按自己组织的 path 前缀裁剪（审批模块同款先例），
 * 树/详情/写操作全部走 assertInView 一处收口。
 * path 与 level 是冗余字段，改上级必须级联重算子树，否则全站数据范围子树查询会错。
 */
@Service
public class OrgServiceImpl implements OrgService {

    @Resource
    private SysOrgMapper sysOrgMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 组织写操作后清子树缓存（插入点①的主动失效口，TTL 兜底之外的及时失效） */
    @Resource
    private OrgScopeSupport orgScopeSupport;

    /** O01 组织树：可见集内组树，父不可见者即为根（部门管理员的子树根） */
    @Override
    public List<OrgNodeVO> tree() {
        //1.取全量未删组织（组织量级百级以内，全量拉取内存组树足够）
        List<SysOrg> orgs = sysOrgMapper.selectList(new QueryWrapper<SysOrg>()
                .eq("deleted", 0).orderByAsc("sort_no", "id"));
        //2.部门管理员裁剪：只留自己组织子树；超管不动
        CurrentUser user = UserHolder.getUser();
        if (!"SUPER_ADMIN".equals(user.getRole())) {
            String scopePath = requireOwnPath(user);
            orgs = orgs.stream()
                    .filter(o -> o.getPath() != null && o.getPath().startsWith(scopePath))
                    .toList();
        }
        //3.批量装配管理员姓名后组树：children 顺序即 sortNo 顺序
        Map<Long, String> managerNames = loadManagerNames(orgs);
        Map<Long, OrgNodeVO> voMap = new HashMap<>();
        for (SysOrg org : orgs) {
            voMap.put(org.getId(), toNode(org, managerNames));
        }
        Set<Long> visibleIds = voMap.keySet();
        List<OrgNodeVO> roots = new ArrayList<>();
        for (SysOrg org : orgs) {
            OrgNodeVO node = voMap.get(org.getId());
            if (org.getParentId() == null || org.getParentId() == 0
                    || !visibleIds.contains(org.getParentId())) {
                roots.add(node);
            } else {
                voMap.get(org.getParentId()).getChildren().add(node);
            }
        }
        return roots;
    }

    /** O05 详情：可见性同树接口 */
    @Override
    public OrgDetailVO detail(Long id) {
        SysOrg org = requireOrg(id);
        assertInView(org);
        Map<Long, String> names = loadManagerNames(List.of(org));
        OrgDetailVO vo = new OrgDetailVO();
        vo.setId(String.valueOf(org.getId()));
        vo.setParentId(String.valueOf(org.getParentId() == null ? 0L : org.getParentId()));
        vo.setName(org.getName());
        vo.setCode(org.getCode());
        vo.setOrgType(org.getOrgType());
        vo.setIsSubOrg(intToBool(org.getIsSubOrg()));
        vo.setManagerId(org.getManagerId());
        vo.setManagerName(names.get(org.getManagerId()));
        vo.setLeaderName(org.getLeaderName());
        vo.setSortNo(org.getSortNo());
        vo.setStatus(org.getStatus());
        vo.setLevel(org.getLevel());
        vo.setPath(org.getPath());
        vo.setRemark(org.getRemark());
        return vo;
    }

    /** O02 创建：雪花 id 先取让 path 一次成型，避免先插后补两跳 */
    @Override
    @Transactional
    public Long create(OrgSaveDTO dto) {
        //1.父组织：0=顶层；否则必须存在且在可视范围内（部门管理员只能建在自己子树下）
        String parentPath;
        int parentLevel;
        if (dto.getParentId() == null || dto.getParentId() == 0) {
            parentPath = "/0/";
            parentLevel = 0;
        } else {
            SysOrg parent = requireOrg(dto.getParentId());
            assertInView(parent);
            parentPath = parent.getPath();
            parentLevel = parent.getLevel() == null ? 0 : parent.getLevel();
        }
        //2.组装实体：id 先取，path/level 一次成型
        Long id = IdWorker.getId();
        SysOrg org = new SysOrg();
        org.setId(id);
        org.setParentId(dto.getParentId() == null ? 0L : dto.getParentId());
        org.setName(dto.getName());
        org.setCode(dto.getCode());
        org.setOrgType(dto.getOrgType());
        org.setIsSubOrg(dto.getIsSubOrg() == null ? 0 : dto.getIsSubOrg());
        org.setManagerId(dto.getManagerId());
        org.setLeaderName(dto.getLeaderName());
        org.setSortNo(dto.getSortNo() == null ? 0 : dto.getSortNo());
        org.setPath(parentPath + id + "/");
        org.setLevel(parentLevel + 1);
        org.setStatus(1);
        org.setRemark(dto.getRemark());
        sysOrgMapper.insert(org);
        //3.新节点会进各祖先组织的子树缓存集：提交后清全族子树缓存（回滚则不清，数据没变）
        orgScopeSupport.evictOrgCacheAfterCommit();
        return id;
    }

    /** O03 编辑：改上级时防循环（3006）并级联重算子树 path/level */
    @Override
    @Transactional
    public void update(Long id, OrgSaveDTO dto) {
        SysOrg org = requireOrg(id);
        assertInView(org);
        //1.改上级：循环校验 + 子树级联
        Long newParentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (!Objects.equals(org.getParentId(), newParentId)) {
            SysOrg newParent = newParentId == 0 ? null : requireOrg(newParentId);
            if (newParent != null) {
                assertInView(newParent);
                //1.1 3006：新上级落在自身子树内（含自己）即循环引用
                if (newParent.getPath() == null || newParent.getPath().startsWith(org.getPath())) {
                    throw new BizException(OrgErrorCode.ORG_PARENT_INVALID);
                }
            }
            //1.2 新自身 path 与层级平移量
            String oldSelfPath = org.getPath();
            String newSelfPath = (newParent == null ? "/0/" : newParent.getPath()) + id + "/";
            int newLevel = (newParent == null ? 0 : newParent.getLevel()) + 1;
            int delta = newLevel - org.getLevel();
            //1.3 子树逐个换前缀 + 平移层级（组织量级小，逐行更新足够）
            List<SysOrg> descendants = sysOrgMapper.selectList(new QueryWrapper<SysOrg>()
                    .likeRight("path", oldSelfPath).ne("id", id).eq("deleted", 0));
            for (SysOrg child : descendants) {
                child.setPath(newSelfPath + child.getPath().substring(oldSelfPath.length()));
                child.setLevel(child.getLevel() + delta);
                sysOrgMapper.updateById(child);
            }
            org.setPath(newSelfPath);
            org.setLevel(newLevel);
            org.setParentId(newParentId);
        }
        //2.基础字段覆盖
        org.setName(dto.getName());
        org.setCode(dto.getCode());
        org.setOrgType(dto.getOrgType());
        org.setIsSubOrg(dto.getIsSubOrg() == null ? 0 : dto.getIsSubOrg());
        org.setManagerId(dto.getManagerId());
        org.setLeaderName(dto.getLeaderName());
        org.setSortNo(dto.getSortNo() == null ? 0 : dto.getSortNo());
        org.setRemark(dto.getRemark());
        sysOrgMapper.updateById(org);
        //3.改上级会平移子树 path，基础字段虽不动结构也统一清——宁可多清不可漏清（提交后执行）
        orgScopeSupport.evictOrgCacheAfterCommit();
    }

    /** O04 删除：3007 子组织拦截 + 人员 org_id 置空（member 同属 A 线，跨表置空走 jdbcTemplate） */
    @Override
    @Transactional
    public void delete(Long id) {
        SysOrg org = requireOrg(id);
        assertInView(org);
        //1.有子组织拒绝删除
        Long childCount = sysOrgMapper.selectCount(new QueryWrapper<SysOrg>()
                .eq("parent_id", id).eq("deleted", 0));
        if (childCount != null && childCount > 0) {
            throw new BizException(OrgErrorCode.ORG_HAS_CHILDREN);
        }
        //2.逻辑删组织 + 该组织人员的 org_id 置空
        sysOrgMapper.deleteById(id);
        jdbcTemplate.update("UPDATE member SET org_id = NULL WHERE org_id = ? AND deleted = 0", id);
        //3.组织没了，各祖先子树缓存集变脏：提交后清全族（插入点①的主动失效）
        orgScopeSupport.evictOrgCacheAfterCommit();
    }

    /** O06 学院静态映射：内置常量直出 */
    @Override
    public List<CollegeVO> colleges() {
        return CollegeCatalog.list();
    }

    // ---------- 私有支撑 ----------

    /** 组织必须存在且未删，否则 1004 */
    private SysOrg requireOrg(Long id) {
        SysOrg org = sysOrgMapper.selectById(id);
        if (org == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "组织不存在");
        }
        return org;
    }

    /** 超管放行；部门管理员仅本组织子树内可见（path 前缀判定） */
    private void assertInView(SysOrg org) {
        CurrentUser user = UserHolder.getUser();
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return;
        }
        String scopePath = requireOwnPath(user);
        if (org.getPath() == null || !org.getPath().startsWith(scopePath)) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "无权访问本部门外的组织");
        }
    }

    /** 部门管理员的组织 path（未绑组织或组织不存在时拒绝，防止越权放行） */
    private String requireOwnPath(CurrentUser user) {
        if (user.getOrgId() == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "当前账号未绑定组织");
        }
        SysOrg own = sysOrgMapper.selectById(user.getOrgId());
        if (own == null || own.getPath() == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "当前账号组织不存在");
        }
        return own.getPath();
    }

    /** 批量装配 manager_id -> 姓名映射（一次查询，避免逐节点 join） */
    private Map<Long, String> loadManagerNames(List<SysOrg> orgs) {
        List<Long> ids = orgs.stream()
                .map(SysOrg::getManagerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            // 必须用 HashMap 而不是 Map.of()：不可变空 Map 的 get(null) 抛 NPE，
            // 而组织没配管理员（manager_id 为 null）是常态——演示数据全是 null，O01 因此 1000
            return new HashMap<>();
        }
        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        return jdbcTemplate.query(
                        "SELECT id, name FROM member WHERE deleted = 0 AND id IN (" + placeholders + ")",
                        (rs, rowNum) -> Map.entry(rs.getLong("id"), rs.getString("name")),
                        ids.toArray())
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private OrgNodeVO toNode(SysOrg org, Map<Long, String> managerNames) {
        OrgNodeVO node = new OrgNodeVO();
        node.setId(String.valueOf(org.getId()));
        node.setParentId(String.valueOf(org.getParentId() == null ? 0L : org.getParentId()));
        node.setName(org.getName());
        node.setOrgType(org.getOrgType());
        node.setIsSubOrg(intToBool(org.getIsSubOrg()));
        node.setManagerName(managerNames.get(org.getManagerId()));
        node.setLeaderName(org.getLeaderName());
        node.setSortNo(org.getSortNo());
        node.setStatus(org.getStatus());
        node.setLevel(org.getLevel());
        return node;
    }

    private Boolean intToBool(Integer value) {
        return value != null && value == 1;
    }
}
