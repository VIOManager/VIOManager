package com.viomanager.dict;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import com.viomanager.dict.entity.SysDictType;
import com.viomanager.dict.mapper.SysDictTypeMapper;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典类型管理 SY01-04：类型列表（预设标记）/新建/改名/删除护栏。
 * 探针类型编码 DTP_ 前缀，事务回滚零残留。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DictTypeManageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysDictTypeMapper sysDictTypeMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private String adminToken;

    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("dtp_admin", "SUPER_ADMIN");
        studentToken = login("dtp_student", "STUDENT");
    }

    @Test
    void sy01ListsPresetTypes() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dicts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        List<Map<String, Object>> types = JsonPath.read(result.getResponse().getContentAsString(), "$.data[*]");
        Map<String, Object> skill = findByCode(types, "SKILL");
        Assertions.assertNotNull(skill, "种子应有 SKILL 类型行");
        Assertions.assertEquals(true, skill.get("preset"), "SKILL 是预设类型");
        Assertions.assertNotNull(findByCode(types, "MATERIAL_TYPE"), "种子应有 MATERIAL_TYPE");
    }

    @Test
    void sy01StudentForbidden() throws Exception {
        mockMvc.perform(get("/api/dicts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void sy02CreatesTypeAndRejectsDuplicate() throws Exception {
        mockMvc.perform(post("/api/dicts")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictCode\":\"DTP_PROBE\",\"dictName\":\"类型探针\"}"))
                .andExpect(jsonPath("$.code").value(0));
        Assertions.assertNotNull(selectByCode("DTP_PROBE"), "创建后应能查到类型");
        mockMvc.perform(post("/api/dicts")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictCode\":\"DTP_PROBE\",\"dictName\":\"重复类型\"}"))
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void sy02StudentForbidden() throws Exception {
        mockMvc.perform(post("/api/dicts")
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictCode\":\"DTP_X\",\"dictName\":\"越权类型\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void sy03RenamesType() throws Exception {
        Long id = insertType("DTP_RENAME", "改名前");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/dicts/types/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictName\":\"改名后\",\"remark\":\"探针\"}"))
                .andExpect(jsonPath("$.code").value(0));
        Assertions.assertEquals("改名后", sysDictTypeMapper.selectById(id).getDictName());
    }

    @Test
    void sy04PresetTypeUndeletable() throws Exception {
        SysDictType skill = selectByCode("SKILL");
        Assertions.assertNotNull(skill);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/dicts/types/" + skill.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void sy04TypeWithItemsUndeletable() throws Exception {
        Long id = insertType("DTP_HAS_ITEMS", "有条目的类型");
        mockMvc.perform(post("/api/dicts/DTP_HAS_ITEMS/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemValue\":\"dtp_v1\",\"itemLabel\":\"占位条目\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/dicts/types/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void sy04EmptyCustomTypeDeletable() throws Exception {
        Long id = insertType("DTP_EMPTY", "空类型");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/dicts/types/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(0));
        Assertions.assertNull(sysDictTypeMapper.selectById(id), "删除后逻辑删不可见");
    }

    // ---------- 支撑 ----------

    private String login(String loginName, String role) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole(role);
        user.setRealName("类型探针-" + loginName);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private Long insertType(String code, String name) {
        SysDictType type = new SysDictType();
        type.setDictCode(code);
        type.setDictName(name);
        sysDictTypeMapper.insert(type);
        return type.getId();
    }

    private SysDictType selectByCode(String code) {
        return sysDictTypeMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysDictType>()
                        .eq("dict_code", code));
    }

    private Map<String, Object> findByCode(List<Map<String, Object>> types, String code) {
        return types.stream().filter(t -> code.equals(t.get("dictCode"))).findFirst().orElse(null);
    }
}
