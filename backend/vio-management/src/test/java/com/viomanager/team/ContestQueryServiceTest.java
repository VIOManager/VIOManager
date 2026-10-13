package com.viomanager.team;

import static org.assertj.core.api.Assertions.assertThat;

import com.viomanager.team.support.ContestContext;
import com.viomanager.team.support.ContestQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * ContestQueryService 三级规则合并单测。
 * 显式列名插入造数；验证 模板默认 → 比赛覆盖 → 赛道覆盖 的逐层覆盖与类型访问器。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContestQueryServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ContestQueryService contestQueryService;

    @Test
    void mergeTemplateContestTrack_threeLevelOverride() {
        long templateId = 4800101L;
        long contestId = 4900101L;
        long trackId = 4700101L;
        // 模板默认：5 人队、prefill 2、禁重复、禁跨队队长、2 指导教师
        jdbcTemplate.update(
                "INSERT INTO contest_template (id, name, code, category, default_rules, status, sort_no, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, 1, 0, 0)",
                templateId, "电控模板", "DK", "电控",
                "{\"memberLimit\":5,\"prefillLimit\":2,\"allowRepeatJoin\":false,"
                        + "\"crossTeamLeader\":false,\"maxAdvisorCount\":2,\"crossTrackLimit\":true}");
        // 比赛覆盖：放开重复参赛、指导教师上限改 3
        jdbcTemplate.update(
                "INSERT INTO contest (id, name, template_id, semester_id, org_id, status, "
                        + "override_rules, source_type, deleted) VALUES (?, ?, ?, ?, NULL, 'REGISTERING', ?, 'MANUAL', 0)",
                contestId, "校电控赛", templateId, 999L,
                "{\"allowRepeatJoin\":true,\"maxAdvisorCount\":3}");
        // 赛道覆盖：人数上限改 3、prefill 改 1
        jdbcTemplate.update(
                "INSERT INTO contest_track (id, contest_id, name, code, override_rules, sort_no, status, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, 0, 1, 0)",
                trackId, contestId, "硬件赛道", "HW",
                "{\"memberLimit\":3,\"prefillLimit\":1}");

        ContestContext ctx = contestQueryService.loadContext(contestId, trackId);

        assertThat(ctx.getStatus()).isEqualTo("REGISTERING");
        assertThat(ctx.getContestName()).isEqualTo("校电控赛");
        assertThat(ctx.getTrackName()).isEqualTo("硬件赛道");
        assertThat(ctx.getTemplateName()).isEqualTo("电控模板");
        // 模板默认：禁跨队队长未被覆盖
        assertThat(ctx.ruleBool("crossTeamLeader", true)).isFalse();
        assertThat(ctx.ruleBool("crossTrackLimit", false)).isTrue();
        // 比赛覆盖：重复参赛放开、指导教师上限 3
        assertThat(ctx.ruleBool("allowRepeatJoin", false)).isTrue();
        assertThat(ctx.ruleInt("maxAdvisorCount", 0)).isEqualTo(3);
        // 赛道覆盖：人数 3、prefill 1
        assertThat(ctx.ruleInt("memberLimit", 0)).isEqualTo(3);
        assertThat(ctx.ruleInt("prefillLimit", 0)).isEqualTo(1);
    }

    @Test
    void noTrack_fallsBackToContestRules() {
        long templateId = 4800201L;
        long contestId = 4900201L;
        jdbcTemplate.update(
                "INSERT INTO contest_template (id, name, code, category, default_rules, status, sort_no, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, 1, 0, 0)",
                templateId, "建模模板", "JM", "建模",
                "{\"memberLimit\":3,\"prefillLimit\":2,\"allowRepeatJoin\":false}");
        jdbcTemplate.update(
                "INSERT INTO contest (id, name, template_id, semester_id, org_id, status, "
                        + "override_rules, source_type, deleted) VALUES (?, ?, ?, ?, NULL, 'REGISTERING', ?, 'MANUAL', 0)",
                contestId, "校建模赛", templateId, 999L, "{\"prefillLimit\":1}");

        ContestContext ctx = contestQueryService.loadContext(contestId, null);

        assertThat(ctx.getTrackName()).isNull();
        assertThat(ctx.ruleInt("memberLimit", 0)).isEqualTo(3);       // 模板默认
        assertThat(ctx.ruleInt("prefillLimit", 0)).isEqualTo(1);      // 比赛覆盖
        assertThat(ctx.ruleBool("allowRepeatJoin", true)).isFalse();  // 模板默认
    }
}
