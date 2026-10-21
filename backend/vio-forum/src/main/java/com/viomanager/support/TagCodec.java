package com.viomanager.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 标签编解码：标签在库里存逗号串（tags 列），出参按数组给前端。
 * 主题与技术交流共用一套规则（null 安全、空段丢弃），改规则只改这里。
 */
public final class TagCodec {

    private TagCodec() {
    }

    /** 标签数组 → 逗号串；全空返回 null（列允许空） */
    public static String join(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String tag : tags) {
            if (tag == null || tag.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(tag);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** 逗号串 → 标签数组；空串/空段不出现在结果里，null 入参返回空数组（不给 null） */
    public static List<String> split(String tags) {
        List<String> list = new ArrayList<>();
        if (tags == null || tags.isEmpty()) {
            return list;
        }
        for (String tag : tags.split(",")) {
            if (!tag.isEmpty()) {
                list.add(tag);
            }
        }
        return list;
    }
}
