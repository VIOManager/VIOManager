package com.viomanager.person.support;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.result.PageResult;
import com.viomanager.dict.service.IDictService;
import com.viomanager.dict.vo.DictItemVO;
import com.viomanager.person.dto.MemberOptionVO;
import com.viomanager.person.dto.PersonQueryDTO;
import com.viomanager.person.dto.PersonVO;
import com.viomanager.person.entity.Person;
import com.viomanager.person.mapper.PersonMapper;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

/**
 * 人员档案查询支撑：筛选拼装、部门管理员子树过滤（Mapper 改 SELF 后拦截器不再注入）、
 * 批量取技能/组织名、M01 分页列表、M14 受限候选搜索，以及 M02 四档视角的出参裁剪（toVO）。
 */
@Component
public class PersonQuerySupport {

    /** M01 分页 size 上限（规范 §2.3） */
    private static final int MAX_PAGE_SIZE = 100;

    /** M14 受限候选搜索 size 上限：比 M01 更紧，防逐页批量枚举全量通讯录 */
    private static final int MAX_OPTIONS_SIZE = 50;

    @Resource
    private PersonSensitiveSupport sensitiveSupport;

    @Resource
    private IDictService dictService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private PersonViewerSupport viewerSupport;

    @Resource
    private PersonMapper personMapper;

    /**
     * M01 分页列表（自 PersonServiceImpl 迁入，查询侧归查询支撑）：
     * 分页兜底 + 筛选拼装 + 部门管理员子树过滤 + 批量技能/组织名 + 列表口径裁剪。
     */
    public PageResult<PersonVO> page(PersonQueryDTO dto) {
        //1.分页参数兜底（规范 §2.3：默认 1/20，size 上限 100）
        int page = dto.getPage() == null || dto.getPage() < 1 ? 1 : dto.getPage();
        int size = dto.getSize() == null || dto.getSize() < 1 ? 20 : Math.min(dto.getSize(), MAX_PAGE_SIZE);
        //2.筛选拼装 + 部门管理员子树过滤（Mapper 改 SELF 后由本支撑负责）
        QueryWrapper<Person> wrapper = buildWrapper(dto);
        applyDeptAdminSubtree(wrapper);
        //3.分页查询
        Page<Person> result = personMapper.selectPage(new Page<>(page, size), wrapper);
        List<Person> records = result.getRecords();
        //4.批量取技能与组织名（防 N+1）后按列表口径裁剪（身份证/证件照/签名/邮箱恒不进列表）
        Map<Long, List<String>> skillsMap = loadSkills(
                records.stream().map(Person::getId).toList());
        Map<Long, String> orgNames = loadOrgNames(
                records.stream().map(Person::getOrgId).filter(Objects::nonNull).distinct().toList());
        List<PersonVO> list = new ArrayList<>(records.size());
        for (Person person : records) {
            PersonVO vo = toVO(person, skillsMap.get(person.getId()), PersonViewerSupport.View.FULL, true);
            vo.setOrgName(orgNames.get(person.getOrgId()));
            list.add(vo);
        }
        return PageResult.of(list, result.getTotal(), page, size);
    }

    /**
     * M14 受限候选搜索（队长流程：预填成员 / 指导教师候选）：
     * 所有登录角色可用；只 select 目录级七字段所需列（组长 SQL 规矩——查一堆只用几个字段是大忌，
     * 敏感列从查询根上就不带出，比出参置空更稳）；只出启用状态成员；keyword 走姓名/学号/学工号模糊。
     */
    public PageResult<MemberOptionVO> options(String keyword, String memberType, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, MAX_OPTIONS_SIZE);
        QueryWrapper<Person> wrapper = new QueryWrapper<>();
        wrapper.select("id", "name", "member_type", "student_no", "job_no", "org_id");
        if (StrUtil.isNotBlank(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("name", kw).or().like("student_no", kw).or().like("job_no", kw));
        }
        if (StrUtil.isNotBlank(memberType)) {
            wrapper.eq("member_type", memberType);
        }
        wrapper.eq("status", 1);
        wrapper.orderByAsc("name");
        Page<Person> result = personMapper.selectPage(new Page<>(p, s), wrapper);
        Map<Long, String> orgNames = loadOrgNames(
                result.getRecords().stream().map(Person::getOrgId).filter(Objects::nonNull).distinct().toList());
        List<MemberOptionVO> list = new ArrayList<>(result.getRecords().size());
        for (Person person : result.getRecords()) {
            MemberOptionVO vo = new MemberOptionVO();
            vo.setId(person.getId());
            vo.setName(person.getName());
            vo.setMemberType(person.getMemberType());
            vo.setStudentNo(person.getStudentNo());
            vo.setJobNo(person.getJobNo());
            vo.setOrgId(person.getOrgId());
            vo.setOrgName(orgNames.get(person.getOrgId()));
            list.add(vo);
        }
        return PageResult.of(list, result.getTotal(), p, s);
    }

    /**
     * M01 筛选拼装：keyword 单搜（姓名/学号/手机号，契约 M01 与前端 mock 口径）、
     * orgId/memberType/hasAccount 精确、skill 按 member_skill 存在性子查询（列名 skill_value，见建表脚本）。
     */
    public QueryWrapper<Person> buildWrapper(PersonQueryDTO dto) {
        QueryWrapper<Person> wrapper = new QueryWrapper<>();
        if (StrUtil.isNotBlank(dto.getKeyword())) {
            String keyword = dto.getKeyword().trim();
            wrapper.and(w -> w.like("name", keyword)
                    .or().like("student_no", keyword)
                    .or().like("phone", keyword));
        }
        if (dto.getOrgId() != null) {
            wrapper.eq("org_id", dto.getOrgId());
        }
        if (StrUtil.isNotBlank(dto.getMemberType())) {
            wrapper.eq("member_type", dto.getMemberType());
        }
        Integer hasAccount = normalizeHasAccount(dto.getHasAccount());
        if (hasAccount != null) {
            wrapper.eq("has_account", hasAccount);
        }
        if (StrUtil.isNotBlank(dto.getSkill())) {
            wrapper.exists("SELECT 1 FROM member_skill ms "
                    + "WHERE ms.member_id = member.id AND ms.skill_value = {0} AND ms.deleted = 0",
                    dto.getSkill().trim());
        }
        return wrapper;
    }

    /**
     * 部门管理员的子树过滤（超管不动）：Mapper 改 SELF 后这层从拦截器挪到了这里。
     * 子树为空（没配组织/组织被删）用 -1 哨兵保证查出空集——IN 空列表在 MyBatis-Plus 里是非法 SQL。
     */
    public void applyDeptAdminSubtree(QueryWrapper<Person> wrapper) {
        CurrentUser user = UserHolder.getUser();
        if (user == null || !"DEPT_ADMIN".equals(user.getRole())) {
            return;
        }
        List<Long> subtree = viewerSupport.querySubtreeOrgIds(user.getOrgId());
        wrapper.in("org_id", subtree.isEmpty() ? List.of(-1L) : subtree);
    }

    /**
     * SKILL 字典当前可用的值集合（M03/M04/M12 落库校验用）：
     * PRESET 预设是系统策展直接可用；用户补充的必须 APPROVED，PENDING/REJECTED 不许挂到人身上。
     */
    public Set<String> approvedSkillValues() {
        List<DictItemVO> items = dictService.listItems("SKILL").getData();
        if (items == null) {
            return Set.of();
        }
        return items.stream()
                .filter(item -> "PRESET".equals(item.getSource())
                        || "APPROVED".equals(item.getAuditStatus()))
                .map(DictItemVO::getItemValue)
                .collect(Collectors.toSet());
    }

    /** 批量取人员技能值（避免逐人查询的 N+1），入参空返回空 Map */
    public Map<Long, List<String>> loadSkills(List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(memberIds.size(), "?"));
        Object[] args = memberIds.toArray();
        Map<Long, List<String>> skillsMap = new HashMap<>();
        RowCallbackHandler rowHandler = rs -> skillsMap.computeIfAbsent(rs.getLong("member_id"), k -> new ArrayList<>())
                .add(rs.getString("skill_value"));
        jdbcTemplate.query("SELECT member_id, skill_value FROM member_skill "
                + "WHERE deleted = 0 AND member_id IN (" + placeholders + ")", rowHandler, args);
        return skillsMap;
    }

    /** 批量取组织名称（列表/详情展示 orgName 用） */
    public Map<Long, String> loadOrgNames(List<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(orgIds.size(), "?"));
        Object[] args = orgIds.toArray();
        Map<Long, String> names = new HashMap<>();
        RowCallbackHandler rowHandler = rs -> names.put(rs.getLong("id"), rs.getString("name"));
        jdbcTemplate.query("SELECT id, name FROM sys_org WHERE deleted = 0 AND id IN (" + placeholders + ")",
                rowHandler, args);
        return names;
    }

    /** 人员绑定账号的登录名（无账号返回 null；仅 FULL 档详情展示用） */
    public String loginNameOf(Long memberId) {
        List<String> names = jdbcTemplate.queryForList(
                "SELECT login_name FROM sys_user WHERE member_id = ? AND deleted = 0", String.class, memberId);
        return names.isEmpty() ? null : names.get(0);
    }

    /**
     * 出参裁剪：按视角填字段。
     * LIMITED 只给姓名/学院/班级/类型/技能；CAPTAIN 与 FULL 差在身份证明文与登录名；
     * forList=true 再叠加 M01 列表口径——身份证/证件照/签名/邮箱永不进列表。
     */
    public PersonVO toVO(Person person, List<String> skills, PersonViewerSupport.View view, boolean forList) {
        PersonVO vo = new PersonVO();
        vo.setId(person.getId());
        vo.setName(person.getName());
        vo.setCollege(person.getCollege());
        vo.setClassName(person.getClassName());
        vo.setMemberType(person.getMemberType());
        vo.setSkills(skills == null ? List.of() : skills);
        if (view == PersonViewerSupport.View.LIMITED) {
            vo.setLimited(true);
            return vo;
        }
        vo.setLimited(false);
        vo.setStudentNo(person.getStudentNo());
        vo.setJobNo(person.getJobNo());
        vo.setMajor(person.getMajor());
        vo.setGender(person.getGender());
        vo.setNation(person.getNation());
        vo.setPhone(person.getPhone());
        vo.setIdCardLast4(person.getIdCardLast4());
        vo.setEmail(person.getEmail());
        vo.setOrgId(person.getOrgId());
        vo.setStatus(person.getStatus());
        vo.setHasAccount(person.getHasAccount());
        vo.setRemark(person.getRemark());
        vo.setCreatedAt(person.getCreatedAt());
        vo.setUpdatedAt(person.getUpdatedAt());
        vo.setPhotoFileId(person.getPhotoFileId());
        vo.setSignatureFileId(person.getSignatureFileId());
        if (view == PersonViewerSupport.View.FULL && !forList) {
            vo.setIdCard(sensitiveSupport.decryptIdCard(person.getIdCard()));
            vo.setLoginName(loginNameOf(person.getId()));
        }
        if (forList) {
            vo.setIdCard(null);
            vo.setEmail(null);
            vo.setPhotoFileId(null);
            vo.setSignatureFileId(null);
            vo.setRemark(null);
        }
        return vo;
    }

    /** hasAccount 筛选归一化：前端传 true/false 字符串，兼容 1/0；其余视为不筛 */
    private Integer normalizeHasAccount(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        return switch (raw.trim()) {
            case "true", "1" -> 1;
            case "false", "0" -> 0;
            default -> null;
        };
    }
}
