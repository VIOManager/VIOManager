package com.viomanager.schedule.support;

/**
 * 名称归一化工具（排班/绩效共用，纯静态无状态）。
 * 课表班级名与档案班级名、导入文件学期名与库内学期名，两两匹配前先过这里——
 * 学校导出的 Excel 常带全角括号、空格、"学年/学期"写法差异，直接字符串比对会漏配。
 */
public final class NameNormalizer {

    private NameNormalizer() {
    }

    /**
     * 班级键归一化：去首尾与内部全部空白（含全角空格）+ 全角字母数字括号转半角。
     * 例："计科（22-1）班 " → "计科(22-1)班"。
     */
    public static String classKey(String raw) {
        if (raw == null) {
            return "";
        }
        return toHalfWidth(raw).replaceAll("\\s+", "");
    }

    /**
     * 学期名归一化：班级键规则之上，统一"学年/学期"分隔与大小写——
     * "2026-2027学年第一学期" 与 "2026－2027 学年 第⼀学期" 应归一为同一键。
     */
    public static String semesterKey(String raw) {
        if (raw == null) {
            return "";
        }
        String s = toHalfWidth(raw).replaceAll("\\s+", "");
        s = s.replace("—", "-").replace("－", "-").replace("～", "-").replace("~", "-");
        s = s.replace("第", "").replace("学年", "-").replace("学期", "");
        return s.toLowerCase();
    }

    /** 全角（FF01–FF5E）转半角，全角空格（U+3000）转普通空格。 */
    private static String toHalfWidth(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c == '\u3000') {
                sb.append(' ');
            } else if (c >= '\uFF01' && c <= '\uFF5E') {
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
