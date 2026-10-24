package com.viomanager.dict;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import com.viomanager.dict.entity.SysDictItem;
import com.viomanager.dict.mapper.SysDictItemMapper;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
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
 * Step 7 验证：字典服务。
 * 覆盖方案验证栏：SKILL 字典能返回预设项 + 用户补充项（含审核状态）。
 * 关键回归点：学生调字典必须放行——mapper 声明了 @DataScope(ALL)，
 * 否则会被数据范围拦截器的"学生默认拒绝"挡掉。
 * @Transactional 每例回滚，字典数据不留开发库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DictServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysDictItemMapper sysDictItemMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        //1.学生账号：字典是全员下拉，用最受限的角色验证放行
        SysUser student = new SysUser();
        student.setLoginName("dict_probe_student");
        student.setPassword(passwordEncoder.encode("pass123456"));
        student.setRole("STUDENT");
        student.setRealName("字典探针用户");
        student.setDefaultPassword(0);
        student.setStatus(1);
        sysUserMapper.insert(student);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"dict_probe_student\",\"password\":\"pass123456\"}"))
                .andReturn();
        studentToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.token");
        //2.SKILL 字典：预设 2 条（已入库）、用户补充 2 条（1 已入库 + 1 待审核）。
        //值统一 dst_ 前缀：活库演示数据（超管在系统配置页自建技能/用户补充）与测试互不干扰
        insertItem("SKILL", "dst_java", "Java 开发", "PRESET", "APPROVED", 1);
        insertItem("SKILL", "dst_python", "Python 开发", "PRESET", "APPROVED", 2);
        insertItem("SKILL", "dst_miniapp", "小程序开发", "USER", "APPROVED", 3);
        insertItem("SKILL", "dst_vue3", "Vue3 前端", "USER", "PENDING", 4);
        //3.不该出现的：停用项、已删项、别的字典（值同样带前缀，防撞活库唯一键）
        insertItem("SKILL", "dst_stopped", "停用项", "PRESET", "APPROVED", 5);
        jdbcTemplate.update(
                "UPDATE sys_dict_item SET status = 0 WHERE dict_code = 'SKILL' AND item_value = 'dst_stopped'");
        insertItem("SKILL", "dst_removed", "已删项", "PRESET", "APPROVED", 6);
        jdbcTemplate.update(
                "UPDATE sys_dict_item SET deleted = 1 WHERE dict_code = 'SKILL' AND item_value = 'dst_removed'");
        insertItem("MATERIAL_TYPE", "dst_plan", "计划书", "PRESET", "APPROVED", 9);
    }

    @Test
    void skillDictReturnsPresetAndUserItems() throws Exception {
        // 学生读 SKILL 字典：探针四条齐全、字段口径对、排序正确，停用/已删/其他字典的不混进来。
        // 断言收窄到 dst_ 值域（活库演示技能可自由增减，互不影响）
        MvcResult result = mockMvc.perform(get("/api/dicts/SKILL")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        List<Map<String, Object>> items = JsonPath.read(result.getResponse().getContentAsString(), "$.data[*]");
        Map<String, Map<String, Object>> byValue = new java.util.HashMap<>();
        for (Map<String, Object> item : items) {
            byValue.put(String.valueOf(item.get("itemValue")), item);
        }
        org.junit.jupiter.api.Assertions.assertTrue(byValue.containsKey("dst_java"), "预设项应在列");
        org.junit.jupiter.api.Assertions.assertEquals("PRESET", byValue.get("dst_java").get("source"));
        org.junit.jupiter.api.Assertions.assertEquals("APPROVED", byValue.get("dst_java").get("auditStatus"));
        // 用户补充项带审核状态返回（方案验证栏）
        org.junit.jupiter.api.Assertions.assertEquals("USER", byValue.get("dst_vue3").get("source"));
        org.junit.jupiter.api.Assertions.assertEquals("PENDING", byValue.get("dst_vue3").get("auditStatus"));
        // 停用项、已删项、其他字典的条目不混进来
        org.junit.jupiter.api.Assertions.assertFalse(byValue.containsKey("dst_stopped"), "停用项不应出现");
        org.junit.jupiter.api.Assertions.assertFalse(byValue.containsKey("dst_removed"), "已删项不应出现");
        org.junit.jupiter.api.Assertions.assertFalse(byValue.containsKey("dst_plan"), "其他字典的条目不应出现");
        // 探针值恰好四条（防重复返回），排序号小的在前
        long probeCount = items.stream().filter(i -> String.valueOf(i.get("itemValue")).startsWith("dst_")).count();
        org.junit.jupiter.api.Assertions.assertEquals(4, probeCount, "探针值应恰好四条");
        int javaIdx = indexOfValue(items, "dst_java");
        int vueIdx = indexOfValue(items, "dst_vue3");
        org.junit.jupiter.api.Assertions.assertTrue(javaIdx < vueIdx, "排序号小的在前");
    }

    /** 在响应列表里找指定值的下标 */
    private int indexOfValue(List<Map<String, Object>> items, String value) {
        for (int i = 0; i < items.size(); i++) {
            if (value.equals(items.get(i).get("itemValue"))) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void unknownCodeReturnsEmptyList() throws Exception {
        // 编码不存在：空数组而非报错——下拉框场景宁空不报错（骨架清单 D01 口径）
        mockMvc.perform(get("/api/dicts/NOT_EXIST")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void requiresLogin() throws Exception {
        // 不带 token：401 + 1002（字典也要登录才能读）
        mockMvc.perform(get("/api/dicts/SKILL"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    /** 插一条字典项 */
    private void insertItem(String dictCode, String itemValue, String itemLabel,
                            String source, String auditStatus, int sortNo) {
        SysDictItem item = new SysDictItem();
        item.setDictCode(dictCode);
        item.setItemValue(itemValue);
        item.setItemLabel(itemLabel);
        item.setSource(source);
        item.setAuditStatus(auditStatus);
        item.setSortNo(sortNo);
        item.setStatus(1);
        sysDictItemMapper.insert(item);
    }
}
