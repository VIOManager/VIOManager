package com.viomanager.approval.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 快照出参净化：雪花 ID 转字符串，普通值与结构原样（B/C 线联调 #4 的出口侧修复）。
 */
class ApprovalPayloadSupportTest {

    private final ApprovalPayloadSupport support = new ApprovalPayloadSupport();

    @Test
    void snowflakeIdBecomesString() {
        assertEquals("{\"teamId\":\"1934123456789012345\",\"memberId\":105}",
                support.sanitize("{\"teamId\":1934123456789012345,\"memberId\":105}"));
    }

    @Test
    void nestedArrayElementsAlsoConverted() {
        assertEquals("{\"prefillMemberIds\":[\"1934123456789012345\",105],\"rate\":33.33}",
                support.sanitize("{\"prefillMemberIds\":[1934123456789012345,105],\"rate\":33.33}"));
    }

    @Test
    void smallNumbersAndStringsUntouched() {
        assertEquals("{\"teamId\":501,\"name\":\"芯火电子队\"}",
                support.sanitize("{\"teamId\":501,\"name\":\"芯火电子队\"}"));
    }

    @Test
    void blankAndInvalidJsonPassThrough() {
        assertNull(support.sanitize(null));
        assertEquals("", support.sanitize(""));
        assertEquals("not-json", support.sanitize("not-json"));
    }
}
