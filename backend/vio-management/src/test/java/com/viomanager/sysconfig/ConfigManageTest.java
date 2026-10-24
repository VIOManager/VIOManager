package com.viomanager.sysconfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import com.viomanager.sysconfig.entity.SysConfig;
import com.viomanager.sysconfig.mapper.SysConfigMapper;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统参数 CF01-02：列表（含种子）/改值/只读护栏/未知键/学生 1003。
 * 断言收窄到种子固定键（site.name / db.schema.version），不锁全表计数。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConfigManageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private String adminToken;

    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("cfg_admin", "SUPER_ADMIN");
        studentToken = login("cfg_student", "STUDENT");
    }

    @Test
    void cf01ListsSeedConfigs() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/configs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        List<Map<String, Object>> configs = JsonPath.read(result.getResponse().getContentAsString(), "$.data[*]");
        Map<String, Object> site = findByKey(configs, "site.name");
        Assertions.assertNotNull(site, "种子应含 site.name");
        Assertions.assertEquals(true, site.get("editable"));
        Map<String, Object> schema = findByKey(configs, "db.schema.version");
        Assertions.assertNotNull(schema, "种子应含只读示例键");
        Assertions.assertEquals(false, schema.get("editable"));
    }

    @Test
    void cf02UpdatesValue() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/configs/site.name")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configValue\":\"cfg_改过的平台名\"}"))
                .andExpect(jsonPath("$.code").value(0));
        Assertions.assertEquals("cfg_改过的平台名", selectByKey("site.name").getConfigValue());
    }

    @Test
    void cf02ReadOnlyRejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/configs/db.schema.version")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configValue\":\"99\"}"))
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void cf02UnknownKeyRejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/configs/cfg_no_such_key")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configValue\":\"x\"}"))
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void studentForbidden() throws Exception {
        mockMvc.perform(get("/api/configs")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    // ---------- 支撑 ----------

    private String login(String loginName, String role) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole(role);
        user.setRealName("参数探针-" + loginName);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private SysConfig selectByKey(String key) {
        return sysConfigMapper.selectOne(new QueryWrapper<SysConfig>().eq("config_key", key));
    }

    private Map<String, Object> findByKey(List<Map<String, Object>> configs, String key) {
        return configs.stream().filter(c -> key.equals(c.get("configKey"))).findFirst().orElse(null);
    }
}
