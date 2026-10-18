package com.viomanager.semester;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
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
 * A 线 Step 5 验证：学期管理 S01-S04（仅超管）。
 * 学期名带 nanoTime 后缀防撞演示数据与并发测试；
 * S03 的"状态字段只归引擎"用 jdbcTemplate 手工改状态后编辑来验证不被覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SemesterServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private String superToken;

    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        superToken = login("sem_probe_sa", "SUPER_ADMIN");
        studentToken = login("sem_probe_stu", "STUDENT");
    }

    @Test
    void createDefaultsAndListNewestFirst() throws Exception {
        // S02+S01：不传周数默认 20、新建固定 PREPARING 非当前；列表新学期在前
        String nameA = "A线测试学期甲" + System.nanoTime();
        String nameB = "A线测试学期乙" + System.nanoTime();
        createSemester("{\"name\":\"" + nameA + "\"}");
        createSemester("{\"name\":\"" + nameB + "\",\"totalWeeks\":18}");
        mockMvc.perform(get("/api/semesters")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value(nameB))
                .andExpect(jsonPath("$.data[0].status").value("PREPARING"))
                .andExpect(jsonPath("$.data[0].isCurrent").value(false))
                .andExpect(jsonPath("$.data[0].totalWeeks").value(18))
                .andExpect(jsonPath("$.data[1].name").value(nameA))
                .andExpect(jsonPath("$.data[1].totalWeeks").value(20));
    }

    @Test
    void duplicateNameRejected() throws Exception {
        // S02 3008：学期名称不可重复
        String name = "A线重复学期" + System.nanoTime();
        createSemester("{\"name\":\"" + name + "\"}");
        mockMvc.perform(post("/api/semesters")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3008));
    }

    @Test
    void updateKeepsEngineOwnedFields() throws Exception {
        // S03：编辑只动基础信息——状态与当前标记是引擎的领地，编辑不得覆盖
        String name = "A线编辑学期" + System.nanoTime();
        long id = createSemester("{\"name\":\"" + name + "\"}");
        jdbcTemplate.update("UPDATE semester SET status = 'RUNNING', is_current = 1 WHERE id = ?", id);
        String newName = name + "改";
        mockMvc.perform(put("/api/semesters/" + id)
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + newName + "\",\"totalWeeks\":16}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, status, is_current, total_weeks FROM semester WHERE id = ?", id);
        Assertions.assertEquals(newName, row.get("name"));
        Assertions.assertEquals("RUNNING", row.get("status"), "编辑不得覆盖状态字段");
        Assertions.assertEquals(1, ((Number) row.get("is_current")).intValue(), "编辑不得覆盖当前标记");
        Assertions.assertEquals(16, ((Number) row.get("total_weeks")).intValue());
    }

    @Test
    void deleteWithContestRejected() throws Exception {
        // S04 3009：学期挂着比赛（四类关联之一）即拒绝删除
        long id = createSemester("{\"name\":\"A线删除保护学期" + System.nanoTime() + "\"}");
        jdbcTemplate.update(
                "INSERT INTO contest (id, name, semester_id, status) VALUES (930091, '探针保护赛', ?, 'DRAFT')",
                id);
        mockMvc.perform(delete("/api/semesters/" + id)
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3009));
    }

    @Test
    void deleteCleanSemesterSucceeds() throws Exception {
        // S04：无关联的学期可删（逻辑删）
        long id = createSemester("{\"name\":\"A线可删学期" + System.nanoTime() + "\"}");
        mockMvc.perform(delete("/api/semesters/" + id)
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        Integer deletedFlag = jdbcTemplate.queryForObject(
                "SELECT deleted FROM semester WHERE id = ?", Integer.class, id);
        Assertions.assertEquals(1, deletedFlag, "学期应被逻辑删除");
    }

    @Test
    void studentForbiddenOnAllSemesterEndpoints() throws Exception {
        // S01-S04 全部仅超管：学生读列表 403，写也 403
        mockMvc.perform(get("/api/semesters")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
        mockMvc.perform(post("/api/semesters")
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"学生越权学期\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    // ---------- 支撑 ----------

    private String login(String loginName, String role) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole(role);
        user.setRealName("学期探针-" + role);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    /** 创建学期并返回新 id（Long 全局序列化成字符串，按 String 读再转） */
    private long createSemester(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/semesters")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return Long.parseLong(JsonPath.read(result.getResponse().getContentAsString(), "$.data"));
    }
}
