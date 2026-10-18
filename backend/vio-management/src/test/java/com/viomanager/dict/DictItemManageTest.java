package com.viomanager.dict;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import com.viomanager.dict.entity.SysDictItem;
import com.viomanager.dict.mapper.SysDictItemMapper;
import jakarta.annotation.Resource;
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
 * A 线 Step 5 验证：技能字典 D02 用户补充 + D03 管理员审核。
 * 值统一 dmt_ 前缀避开演示数据与其他测试（SKILL 字典不许留手工持久行的教训）。
 * 二次审核的 1006 用"新幂等键二次请求"验证——幂等键由前端传入，换键即可穿透切面打到状态机。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DictItemManageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysDictItemMapper sysDictItemMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private String studentToken;

    private String adminToken;

    private Long pendingItemId;

    @BeforeEach
    void setUp() throws Exception {
        studentToken = login("dmt_student", "STUDENT", null);
        adminToken = login("dmt_admin", "SUPER_ADMIN", null);
        // 预设项一条（占住最大 sortNo）+ 待审核项一条（审核用例的靶子）
        insertItem("dmt_java", "探针预设技能", "PRESET", "APPROVED", 50);
        SysDictItem pending = insertItem("dmt_vue", "探针待审技能", "USER", "PENDING", 51);
        pendingItemId = pending.getId();
    }

    @Test
    void studentSubmitsNewItemAsPending() throws Exception {
        // D02：学生提交新技能 → USER 来源 + PENDING 审核 + 排到最大 sortNo 之后
        mockMvc.perform(post("/api/dicts/SKILL/items")
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemValue\":\"dmt_unity\",\"itemLabel\":\"Unity 开发\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        SysDictItem created = selectByValue("dmt_unity");
        Assertions.assertNotNull(created, "提交后应能查到新字典项");
        Assertions.assertEquals("USER", created.getSource());
        Assertions.assertEquals("PENDING", created.getAuditStatus());
        Assertions.assertEquals(52, created.getSortNo());
    }

    @Test
    void duplicateValueRejectedIncludingPending() throws Exception {
        // D02 查重口径：值撞上"待审核"项也要拒——防止换个马甲重复提交
        mockMvc.perform(post("/api/dicts/SKILL/items")
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemValue\":\"dmt_vue\",\"itemLabel\":\"重复技能\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void adminApprovesPendingItem() throws Exception {
        // D03 通过：PENDING → APPROVED
        mockMvc.perform(post("/api/dicts/items/" + pendingItemId + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        Assertions.assertEquals("APPROVED",
                sysDictItemMapper.selectById(pendingItemId).getAuditStatus());
    }

    @Test
    void adminRejectsPendingItem() throws Exception {
        // D03 驳回：PENDING → REJECTED
        mockMvc.perform(post("/api/dicts/items/" + pendingItemId + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        Assertions.assertEquals("REJECTED",
                sysDictItemMapper.selectById(pendingItemId).getAuditStatus());
    }

    @Test
    void auditAlreadyHandledItemRejected() throws Exception {
        // 条件更新防重复审核：第一次审掉 PENDING 后，换幂等键再审同一项 → 1006
        String auditUrl = "/api/dicts/items/" + pendingItemId + "/audit";
        mockMvc.perform(post(auditUrl)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post(auditUrl)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void studentCannotAudit() throws Exception {
        // 审核是管理员动作：学生 403 + 1003
        mockMvc.perform(post("/api/dicts/items/" + pendingItemId + "/audit")
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void unauthenticatedSubmitRejected() throws Exception {
        // 不带 token：401 + 1002（补充技能虽是全员功能，也要登录）
        mockMvc.perform(post("/api/dicts/SKILL/items")
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemValue\":\"dmt_x\",\"itemLabel\":\"未登录提交\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    // ---------- 支撑 ----------

    /** 注册探针账号并登录拿 token */
    private String login(String loginName, String role, Long orgId) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole(role);
        user.setRealName("字典审核探针-" + role);
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

    private SysDictItem insertItem(String itemValue, String itemLabel,
                                   String source, String auditStatus, int sortNo) {
        SysDictItem item = new SysDictItem();
        item.setDictCode("SKILL");
        item.setItemValue(itemValue);
        item.setItemLabel(itemLabel);
        item.setSource(source);
        item.setAuditStatus(auditStatus);
        item.setSortNo(sortNo);
        item.setStatus(1);
        sysDictItemMapper.insert(item);
        return item;
    }

    private SysDictItem selectByValue(String itemValue) {
        return sysDictItemMapper.selectOne(new QueryWrapper<SysDictItem>()
                .eq("dict_code", "SKILL").eq("item_value", itemValue).eq("deleted", 0));
    }
}
