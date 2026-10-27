package com.viomanager.ai.support;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * RAG 中文分块器（方案 §六）：按 markdown 标题分节优先，小节贪心合并、超长节滑窗切割。
 * 刻意没用 Spring AI 的 TokenTextSplitter——它按 token 计数对中文块长不稳（1 字 ≈ 1-2 token），
 * 且无标题感知；使用指南语料是结构化 md，标题分节 + 字数切割的块长可控可测（方案验收：
 * 90% 块落在 200-400 字区间）。
 */
@Component
public class RagChunker {

    /** 单块硬上限（方案 §六：目标 300 字，验收区间 200-400，取 400 为窗口大小） */
    public static final int MAX_CHARS = 400;

    /** 滑窗切割相邻块重叠：防关键句被切断在块边界（方案 §六） */
    public static final int OVERLAP = 50;

    public List<String> chunk(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        for (String section : mergeSmallSections(splitByHeading(markdown))) {
            windowSplit(section, chunks);
        }
        return chunks;
    }

    /** 按标题行分节：# 开头的行起新节，首个标题前的前言也是一节 */
    private List<String> splitByHeading(String markdown) {
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : markdown.split("\n", -1)) {
            if (!current.isEmpty() && line.startsWith("#")) {
                sections.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(line).append('\n');
        }
        if (!current.isEmpty()) {
            sections.add(current.toString().trim());
        }
        return sections;
    }

    /** 贪心合并相邻小节到 MAX_CHARS 内：防"三个百字小节=三个碎片块"污染检索粒度 */
    private List<String> mergeSmallSections(List<String> sections) {
        List<String> merged = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String section : sections) {
            if (section.isEmpty()) {
                continue;
            }
            if (!buf.isEmpty() && buf.length() + section.length() + 1 > MAX_CHARS) {
                merged.add(buf.toString());
                buf = new StringBuilder();
            }
            if (!buf.isEmpty()) {
                buf.append('\n');
            }
            buf.append(section);
        }
        if (!buf.isEmpty()) {
            merged.add(buf.toString());
        }
        return merged;
    }

    /** 超长节滑窗切割：窗口 MAX_CHARS、步进 MAX_CHARS-OVERLAP，末块不小于重叠量 */
    private void windowSplit(String section, List<String> out) {
        if (section.length() <= MAX_CHARS) {
            out.add(section);
            return;
        }
        int step = MAX_CHARS - OVERLAP;
        for (int start = 0; start < section.length(); start += step) {
            int end = Math.min(start + MAX_CHARS, section.length());
            out.add(section.substring(start, end));
            if (end == section.length()) {
                break;
            }
        }
    }
}
