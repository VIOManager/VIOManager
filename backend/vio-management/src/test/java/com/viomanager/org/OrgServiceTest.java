package com.viomanager.org;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import com.viomanager.org.entity.SysOrg;
import com.viomanager.org.mapper.SysOrgMapper;
import java.util.List;
import java.util.Map;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * A 线 Step 5 验证：组织管理 O01-O06。
 * 探针组织固定 id 920001-920004（A→B→C 三层链 + 独立根 D），
 * 部门管理员绑定 B 验证"仅本部门子树"裁剪：可见集 = {B, C}，A 与 D 不可见。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrgServiceTest {

    private static final long ORG_A = 920001L;
    private static final long ORG_B = 920002L;
    private static final long ORG_C = 920003L;
    private static final long ORG_D = 920004L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysOrgMapper sysOrgMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private String superToken;

    private String deptAdminToken;

    @BeforeEach
    void setUp() throws Exception {
        superToken = login("org_probe_sa", "SUPER_ADMIN", null);
        deptAdminToken = login("org_probe_dept", "DEPT_ADMIN", ORG_B);
        insertOrg(ORG_A, 0L, "探针根组织A", "/0/920001/", 1);
        insertOrg(ORG_B, ORG_A, "探针子组织B", "/0/920001/920002/", 2);
        insertOrg(ORG_C, ORG_B, "探针孙组织C", "/0/920001/920002/920003/", 3);
        insertOrg(ORG_D, 0L, "探针独立根D", "/0/920004/", 1);
    }

    @Test
    void superAdminSeesProbeHierarchy() throws Exception {
        // O01 超管：树里能看到探针三层链的嵌套关系
        MvcResult result = mockMvc.perform(get("/api/orgs/tree")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        List<Map<String, Object>> roots = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data");
        Map<String, Object> nodeA = findNode(roots, String.valueOf(ORG_A));
        Map<String, Object> nodeB = findNode(roots, String.valueOf(ORG_B));
        Map<String, Object> nodeC = findNode(roots, String.valueOf(ORG_C));
        Assertions.assertNotNull(nodeA, "超管应看到探针根A");
        Assertions.assertNotNull(nodeC, "超管应看到探针孙C");
        // B 必须挂在 A 的 children 里，C 挂在 B 的 children 里（层级正确）
        Assertions.assertTrue(containsChild(nodeA, ORG_B), "B 应是 A 的子节点");
        Assertions.assertTrue(containsChild(nodeB, ORG_C), "C 应是 B 的子节点");
    }

    @Test
    void deptAdminSeesOnlyOwnSubtree() throws Exception {
        // O01 部门管理员（绑定 B）：根就是 B，只有 {B, C}，看不到 A 和 D
        mockMvc.perform(get("/api/orgs/tree")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(String.valueOf(ORG_B)))
                .andExpect(jsonPath("$.data[0].children[0].id").value(String.valueOf(ORG_C)));
    }

    @Test
    void createBuildsPathAndLevel() throws Exception {
        // O02：挂在 A 下创建 → path=/0/A/新id/，level=父级+1，状态启用
        MvcResult result = mockMvc.perform(post("/api/orgs")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"920001\",\"name\":\"探针新建组\","
                                + "\"orgType\":\"DEPARTMENT\",\"sortNo\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long newId = Long.parseLong(
                JsonPath.read(result.getResponse().getContentAsString(), "$.data"));
        SysOrg created = sysOrgMapper.selectById(newId);
        Assertions.assertEquals("/0/" + ORG_A + "/" + newId + "/", created.getPath());
        Assertions.assertEquals(2, created.getLevel());
        Assertions.assertEquals(1, created.getStatus());
    }

    @Test
    void createWithMissingParentRejected() throws Exception {
        // O02：上级不存在 → 1004
        mockMvc.perform(post("/api/orgs")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"999999\",\"name\":\"孤儿组织\","
                                + "\"orgType\":\"DEPARTMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void moveUnderOwnDescendantRejected() throws Exception {
        // O03 3006：把 A 挂到自己的孙节点 C 下 = 循环引用
        mockMvc.perform(put("/api/orgs/" + ORG_A)
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"920003\",\"name\":\"探针根组织A改\","
                                + "\"orgType\":\"DEPARTMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3006));
    }

    @Test
    void deleteWithChildrenRejected() throws Exception {
        // O04 3007：A 下还有 B，拒绝删除
        mockMvc.perform(delete("/api/orgs/" + ORG_A)
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3007));
    }

    @Test
    void deleteLeafClearsMemberOrgBinding() throws Exception {
        // O04：删叶子 C → C 逻辑删 + 该组织人员 org_id 置空
        jdbcTemplate.update("INSERT INTO member (id, name, org_id) VALUES (920099, '探针成员', ?)", ORG_C);
        mockMvc.perform(delete("/api/orgs/" + ORG_C)
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        Integer deletedFlag = jdbcTemplate.queryForObject(
                "SELECT deleted FROM sys_org WHERE id = ?", Integer.class, ORG_C);
        Long memberOrg = jdbcTemplate.queryForObject(
                "SELECT org_id FROM member WHERE id = 920099", Long.class);
        Assertions.assertEquals(1, deletedFlag, "组织应被逻辑删除");
        Assertions.assertNull(memberOrg, "成员 org_id 应被置空");
    }

    @Test
    void collegesCatalogReturnsThirteenColleges() throws Exception {
        // O06：静态学院映射——13 个学院、每个学院至少 1 个专业
        MvcResult result = mockMvc.perform(get("/api/orgs/colleges")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        List<Map<String, Object>> colleges = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data");
        Assertions.assertEquals(13, colleges.size(), "学院数应为 13");
        for (Map<String, Object> college : colleges) {
            List<?> majors = (List<?>) college.get("majors");
            Assertions.assertFalse(majors.isEmpty(), "每个学院都应有专业：" + college.get("college"));
        }
    }

    @Test
    void deptAdminDetailOutsideSubtreeForbidden() throws Exception {
        // O05 数据范围：部门管理员查子树外的 D → 403；查自己子树内的 C → 200
        mockMvc.perform(get("/api/orgs/" + ORG_D)
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
        mockMvc.perform(get("/api/orgs/" + ORG_C)
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("探针孙组织C"));
    }

    // ---------- 支撑 ----------

    private String login(String loginName, String role, Long orgId) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole(role);
        user.setRealName("组织探针-" + role);
        user.setOrgId(orgId);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private void insertOrg(long id, long parentId, String name, String path, int level) {
        SysOrg org = new SysOrg();
        org.setId(id);
        org.setParentId(parentId);
        org.setName(name);
        org.setCode("PROBE-" + id);
        org.setOrgType("DEPARTMENT");
        org.setIsSubOrg(0);
        org.setSortNo(1);
        org.setPath(path);
        org.setLevel(level);
        org.setStatus(1);
        sysOrgMapper.insert(org);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findNode(List<Map<String, Object>> nodes, String id) {
        for (Map<String, Object> node : nodes) {
            if (id.equals(node.get("id"))) {
                return node;
            }
            Map<String, Object> hit = findNode(
                    (List<Map<String, Object>>) node.getOrDefault("children", List.of()), id);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private boolean containsChild(Map<String, Object> parent, long childId) {
        List<?> children = (List<?>) parent.getOrDefault("children", List.of());
        for (Object child : children) {
            if (String.valueOf(childId).equals(((Map<?, ?>) child).get("id"))) {
                return true;
            }
        }
        return false;
    }
}
