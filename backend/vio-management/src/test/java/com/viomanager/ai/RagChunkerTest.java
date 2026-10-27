package com.viomanager.ai;

import com.viomanager.ai.support.RagChunker;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * RAG 分块器单测（方案 §六验收）：标题分节、小节贪心合并、超长滑窗 50 字重叠。
 */
class RagChunkerTest {

    private final RagChunker chunker = new RagChunker();

    @Test
    void shouldMergeSmallSections() {
        String md = "# 标题甲\n" + "甲内容。".repeat(20)
                + "\n# 标题乙\n" + "乙内容。".repeat(20);
        List<String> chunks = chunker.chunk(md);
        Assertions.assertEquals(1, chunks.size(), "两个百字小节应贪心合并成一块（防碎片块污染检索）");
        Assertions.assertTrue(chunks.get(0).contains("标题甲") && chunks.get(0).contains("标题乙"));
    }

    @Test
    void shouldKeepBigSectionsInSeparateChunks() {
        String md = "# 标题甲\n" + "甲内容。".repeat(70)
                + "\n# 标题乙\n" + "乙内容。".repeat(70);
        List<String> chunks = chunker.chunk(md);
        Assertions.assertEquals(2, chunks.size(), "两个约 300 字节合并超上限应各成一块");
        Assertions.assertTrue(chunks.get(0).contains("甲内容"));
        Assertions.assertTrue(chunks.get(1).contains("乙内容"));
    }

    @Test
    void shouldWindowSplitLongSectionWithOverlap() {
        String section = "# 长文\n" + "材料提交说明文字。".repeat(80);
        List<String> chunks = chunker.chunk(section);
        Assertions.assertEquals(2, chunks.size());
        Assertions.assertEquals(RagChunker.MAX_CHARS, chunks.get(0).length());
        //重叠验收：块1 末尾 50 字与块2 开头 50 字完全一致（关键句跨边界可两块命中）
        String tail = chunks.get(0).substring(chunks.get(0).length() - RagChunker.OVERLAP);
        Assertions.assertEquals(tail, chunks.get(1).substring(0, RagChunker.OVERLAP));
        Assertions.assertTrue(chunks.get(1).length() >= RagChunker.OVERLAP, "末块不应短于重叠量");
    }

    @Test
    void shouldReturnEmptyForBlankInput() {
        Assertions.assertTrue(chunker.chunk(null).isEmpty());
        Assertions.assertTrue(chunker.chunk("   \n  ").isEmpty());
    }
}
