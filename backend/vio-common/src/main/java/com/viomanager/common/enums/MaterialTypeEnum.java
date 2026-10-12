package com.viomanager.common.enums;

/**
 * 材料类型枚举（12 种预设值，MR01 添加材料要求的 materialType 取值域）。
 * 码值与中文含义严格照 C 线接口清单《C线-材料成果-接口清单.md》第 2 章「材料任务与要求」；
 * 新增或改名必须先改接口清单再改本枚举，两端保持一致。
 *
 * <p>code 为数据库/接口传输用的英文字符串，label 为前端展示用的中文。
 *
 * <p>⚠️ 本文件位于 common.enums 属**公共组件**：按规范 §7.6，开 PR 前须在群里报备。
 */
public enum MaterialTypeEnum {

    REGISTRATION_FORM("REGISTRATION_FORM", "报名表"),

    PROJECT_PLAN("PROJECT_PLAN", "项目书"),

    COMMITMENT("COMMITMENT", "承诺书"),

    ID_COPY("ID_COPY", "身份证复印件"),

    STUDENT_CARD_COPY("STUDENT_CARD_COPY", "学生证复印件"),

    TEAM_PHOTO("TEAM_PHOTO", "团队合照"),

    DEFENSE_PPT("DEFENSE_PPT", "答辩PPT"),

    RESEARCH_REPORT("RESEARCH_REPORT", "研究报告"),

    SOURCE_CODE("SOURCE_CODE", "源代码"),

    AWARD_CERT("AWARD_CERT", "获奖证书"),

    EVIDENCE("EVIDENCE", "佐证材料"),

    OTHER("OTHER", "其他");

    private final String code;
    private final String label;

    MaterialTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 判断 code 是否为已登记的材料类型；入参为 null 直接返回 false，不抛异常。
     */
    public static boolean isValid(String code) {
        return of(code) != null;
    }

    /**
     * 按 code 查枚举；null 或未登记的 code 一律返回 null，由调用方判空处理。
     */
    public static MaterialTypeEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (MaterialTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 按 code 查中文展示名；找不到（含入参为 null）时**原样返回入参**而非 null：
     * 展示链路兜底显示原始字符串，避免页面渲染出 "null" 字样。
     * 需严格区分合法/非法的场景请改用 {@link #isValid(String)} 或 {@link #of(String)}。
     */
    public static String labelOf(String code) {
        MaterialTypeEnum value = of(code);
        return value == null ? code : value.label;
    }
}
