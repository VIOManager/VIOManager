package com.viomanager.schedule.support;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 班级名宽容匹配（课表导入协调拍板：教务导出与人员档案是两套班级名写法，靠 class_mapping 映射桥接）。
 * 三层评分：3 精确归一化键 → 2 结构化键（专业+年号+序号，「软件2301班」≡「软件23-1班」）
 * → 1 同年号序号且专业名互含（全称↔简称：「物联网25-1班」↔「物联25-1班」、「大数据」↔「数据」）。
 * uniqueMatch 只在最佳层有唯一候选时回填（多候选不猜，留人工在映射确认环节处理）；
 * 映射确认（confirmed=1）始终是班级参与空课统计的唯一闸门，本类产出的是建议值。
 */
public final class ClassNameMatcher {

    private ClassNameMatcher() {
    }

    /** 尾部年号+序号（横线可省、班可省）：软件23-1班 / 软件2301班 → (软件, 23-01)。 */
    private static final Pattern TAIL = Pattern.compile("(\\d{2})-?(\\d{1,2})班?$");

    /** 专升本等 S 班（财会S2025班）：尾部四位是完整年份而非年号+序号，不参与结构化拆分。 */
    private static final Pattern S_CLASS = Pattern.compile("S\\d{4}(班)?$");

    /** 在候选班级名中找与 from 同指一个班的唯一候选；无匹配或最佳层多候选时返回 null（不猜）。 */
    public static String uniqueMatch(String from, Collection<String> candidates) {
        if (from == null || from.isBlank() || candidates == null || candidates.isEmpty()) {
            return null;
        }
        String fromKey = NameNormalizer.classKey(from);
        String[] fromParsed = parse(fromKey);
        String best = null;
        int bestScore = 0;
        int bestCount = 0;
        for (String candidate : dedupe(candidates)) {
            int score = score(fromKey, fromParsed, candidate);
            if (score == 0) {
                continue;
            }
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
                bestCount = 1;
            } else if (score == bestScore) {
                bestCount++;
            }
        }
        return bestCount == 1 ? best : null;
    }

    /** 两班级名是否同指一个班（三层任一命中即真；无法判定唯一性时慎用于自动绑定，优先 uniqueMatch）。 */
    public static boolean sameClass(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return score(NameNormalizer.classKey(a), null, b) > 0
                || score(NameNormalizer.classKey(b), null, a) > 0;
    }

    private static int score(String fromKey, String[] fromParsed, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return 0;
        }
        String candidateKey = NameNormalizer.classKey(candidate);
        if (fromKey.equals(candidateKey)) {
            return 3;
        }
        String[] parsed = fromParsed != null ? fromParsed : parse(fromKey);
        if (parsed == null) {
            return 0;
        }
        String[] candidateParsed = parse(candidateKey);
        if (candidateParsed == null || !parsed[1].equals(candidateParsed[1])) {
            return 0;
        }
        if (parsed[0].equals(candidateParsed[0])) {
            return 2;
        }
        //年号序号相同、专业名一个是另一个的子串 → 全称/简称（物联网↔物联、大数据↔数据）
        return parsed[0].contains(candidateParsed[0]) || candidateParsed[0].contains(parsed[0]) ? 1 : 0;
    }

    /** 拆 [专业, 年号-序号]；S 班、拆不出年号序号、专业为空 → null。 */
    private static String[] parse(String key) {
        if (S_CLASS.matcher(key).find()) {
            return null;
        }
        Matcher matcher = TAIL.matcher(key);
        if (!matcher.find() || matcher.start() == 0) {
            return null;
        }
        String major = key.substring(0, matcher.start());
        if (major.isEmpty()) {
            return null;
        }
        return new String[]{major, matcher.group(1) + "-" + String.format("%02d", Integer.parseInt(matcher.group(2)))};
    }

    private static Set<String> dedupe(Collection<String> candidates) {
        Set<String> set = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                set.add(candidate);
            }
        }
        return set;
    }
}
