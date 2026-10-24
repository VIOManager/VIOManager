package com.viomanager.dict;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import com.viomanager.dict.entity.SysDictItem;
import com.viomanager.dict.entity.SysDictType;
import com.viomanager.dict.mapper.SysDictItemMapper;
import com.viomanager.dict.mapper.SysDictTypeMapper;
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
 * 字典条目管理 SY05-10 + D02/D03 前端兼容收口：
 * 超管直建预置项 / 学生只发 label 的别名提交 / 仅 SKILL 可补充 / 启停影响 D01 /
 * PUT 审核与 POST 审核（pass 别名）并存。
 * 探针值 dia_ 前缀（SKILL 字典不许留手工持久行的教训，事务回滚零残留）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DictItemAdminTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysDictTypeMapper sysDictTypeMapper;

    @Autowired
    private SysDictItemMapper sysDictItemMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private String adminToken;

    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("dia_admin", "SUPER_ADMIN");
        studentToken = login("dia_student", "STUDENT");
        SysDictType type = new SysDictType();
        type.setDictCode("DIA_PROBE");
        type.setDictName("条目探针类型");
        sysDictTypeMapper.insert(type);
    }

    @Test
    void sy06AdminCreatesPresetItemDirectly() throws Exception {
        mockMvc.perform(post("/api/dicts/DIA_PROBE/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemValue\":\"dia_a\",\"itemLabel\":\"条目A\"}"))
                .andExpect(jsonPath("$.code").value(0));
        SysDictItem item = selectByValue("DIA_PROBE", "dia_a");
        Assertions.assertNotNull(item);
        Assertions.assertEquals("PRESET", item.getSource(), "超管建的是预置项");
        Assertions.assertEquals("APPROVED", item.getAuditStatus(), "预置项直接生效");
        Assertions.assertEquals(1, item.getSortNo(), "新类型第一条排序号为 1");
    }

    @Test
    void sy06DuplicateValueRejected() throws Exception {
        createPresetItem("dia_dup", "重复值");
        mockMvc.perform(post("/api/dicts/DIA_PROBE/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemValue\":\"dia_dup\",\"itemLabel\":\"再建一次\"}"))
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void d02StudentLabelAliasSubmitsPending() throws Exception {
        //A 线面板只发 {label}：值缺省取显示名，入库 USER + PENDING
        mockMvc.perform(post("/api/dicts/SKILL/items")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"dia_别名技能\"}"))
                .andExpect(jsonPath("$.code").value(0));
        SysDictItem item = selectByValue("SKILL", "dia_别名技能");
        Assertions.assertNotNull(item, "值应取显示名同值");
        Assertions.assertEquals("dia_别名技能", item.getItemLabel());
        Assertions.assertEquals("USER", item.getSource());
        Assertions.assertEquals("PENDING", item.getAuditStatus());
    }

    @Test
    void d02StudentOnlySkillAllowed() throws Exception {
        mockMvc.perform(post("/api/dicts/DIA_PROBE/items")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"dia_越权补充\"}"))
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void sy05AdminItemsListWithFilters() throws Exception {
        createPresetItem("dia_list1", "在列条目");
        SysDictItem disabled = createPresetItem("dia_list2", "停用条目");
        SysDictItem pending = insertItem("SKILL", "dia_list3", "待审条目", "USER", "PENDING", 90);
        mockMvc.perform(get("/api/dicts/DIA_PROBE/items")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].itemValue").value("dia_list1"))
                .andExpect(jsonPath("$.data[1].status").value("ENABLED"));
        //停用项在管理视角可见（D01 才排除）——包含式断言，活库演示数据（如待审的机器人开发）可自由共存
        mockMvc.perform(get("/api/dicts/DIA_PROBE/items").param("keyword", "停用")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data[?(@.id == '" + disabled.getId() + "')]").isNotEmpty());
        mockMvc.perform(get("/api/dicts/SKILL/items").param("auditStatus", "PENDING")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data[?(@.id == '" + pending.getId() + "')]").isNotEmpty());
    }

    @Test
    void sy07RenamesItem() throws Exception {
        SysDictItem item = createPresetItem("dia_rename", "改名前");
        mockMvc.perform(MockMvcRequestBuilders.put("/api/dicts/items/" + item.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemLabel\":\"改名后\"}"))
                .andExpect(jsonPath("$.code").value(0));
        Assertions.assertEquals("改名后", sysDictItemMapper.selectById(item.getId()).getItemLabel());
    }

    @Test
    void sy09DisabledItemLeavesD01() throws Exception {
        SysDictItem item = createPresetItem("dia_toggle", "启停条目");
        mockMvc.perform(MockMvcRequestBuilders.put("/api/dicts/items/" + item.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(jsonPath("$.code").value(0));
        MvcResult d01 = mockMvc.perform(get("/api/dicts/DIA_PROBE")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn();
        List<String> values = JsonPath.read(d01.getResponse().getContentAsString(), "$.data[*].itemValue");
        Assertions.assertFalse(values.contains("dia_toggle"), "停用项不再出现在 D01");
    }

    @Test
    void sy10AuditsUserPendingItem() throws Exception {
        SysDictItem pending = insertItem("SKILL", "dia_audit", "待审词条", "USER", "PENDING", 91);
        mockMvc.perform(MockMvcRequestBuilders.put("/api/dicts/items/" + pending.getId() + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":\"APPROVED\",\"comment\":\"通过\"}"))
                .andExpect(jsonPath("$.code").value(0));
        Assertions.assertEquals("APPROVED", sysDictItemMapper.selectById(pending.getId()).getAuditStatus());
        //SY10 再审已审项 → 1006
        mockMvc.perform(MockMvcRequestBuilders.put("/api/dicts/items/" + pending.getId() + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":\"REJECTED\"}"))
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void sy10PresetItemNotAuditable() throws Exception {
        SysDictItem preset = createPresetItem("dia_preset_noaudit", "预置不可审");
        mockMvc.perform(MockMvcRequestBuilders.put("/api/dicts/items/" + preset.getId() + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":\"APPROVED\"}"))
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void d03PassAliasStillWorks() throws Exception {
        SysDictItem pending = insertItem("SKILL", "dia_pass", "别名审核", "USER", "PENDING", 92);
        mockMvc.perform(post("/api/dicts/items/" + pending.getId() + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pass\":true}"))
                .andExpect(jsonPath("$.code").value(0));
        Assertions.assertEquals("APPROVED", sysDictItemMapper.selectById(pending.getId()).getAuditStatus());
    }

    @Test
    void sy08DeletesItem() throws Exception {
        SysDictItem item = createPresetItem("dia_del", "待删条目");
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/dicts/items/" + item.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(0));
        Assertions.assertNull(sysDictItemMapper.selectById(item.getId()), "删除后逻辑删不可见");
    }

    @Test
    void d01VoCarriesIdAndLabelAlias() throws Exception {
        createPresetItem("dia_vo", "别名字段条目");
        MvcResult result = mockMvc.perform(get("/api/dicts/DIA_PROBE")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn();
        List<Map<String, Object>> items = JsonPath.read(result.getResponse().getContentAsString(), "$.data[*]");
        Assertions.assertFalse(items.isEmpty());
        Map<String, Object> first = items.get(0);
        Assertions.assertNotNull(first.get("id"), "D01 应带 id（A 线面板行键/审核目标）");
        Assertions.assertEquals(first.get("itemLabel"), first.get("label"), "label 别名与 itemLabel 同值");
    }

    // ---------- 支撑 ----------

    private String login(String loginName, String role) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole(role);
        user.setRealName("条目探针-" + loginName);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private SysDictItem createPresetItem(String value, String label) throws Exception {
        mockMvc.perform(post("/api/dicts/DIA_PROBE/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemValue\":\"" + value + "\",\"itemLabel\":\"" + label + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
        return selectByValue("DIA_PROBE", value);
    }

    private SysDictItem insertItem(String code, String value, String label,
                                   String source, String auditStatus, int sortNo) {
        SysDictItem item = new SysDictItem();
        item.setDictCode(code);
        item.setItemValue(value);
        item.setItemLabel(label);
        item.setSource(source);
        item.setAuditStatus(auditStatus);
        item.setSortNo(sortNo);
        item.setStatus(1);
        sysDictItemMapper.insert(item);
        return item;
    }

    private SysDictItem selectByValue(String code, String value) {
        return sysDictItemMapper.selectOne(new QueryWrapper<SysDictItem>()
                .eq("dict_code", code).eq("item_value", value).eq("deleted", 0));
    }
}
