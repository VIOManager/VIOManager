package com.viomanager.material.vo;

import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * zip 批量上传出参（MS03）：匹配到的要求清单 + 没匹配上的条目名清单。
 * matched 含"已交过被跳过"的要求——体现 zip 覆盖了哪些要求（未重复覆盖），是否真正新落库以提交记录为准。
 * unmatched 在兜底层（存储不可用拿不到条目）里即压缩包名本身：包名一个要求都没匹配上时才有值。
 */
@Data
@Accessors(chain = true)
public class BatchMatchResultVO {

    /** 匹配到的要求（含已交过被跳过、未重复覆盖的） */
    private List<MatchedItem> matched;

    /** 没匹配到任何要求的条目名（兜底层里即压缩包名本身） */
    private List<String> unmatched;

    /** 单条匹配结果 */
    @Data
    @Accessors(chain = true)
    public static class MatchedItem {

        /** 材料要求 ID（JSON 序列化为字符串） */
        private Long requirementId;

        /** 材料类型（12 种预设枚举之一） */
        private String materialType;

        /** 要求名称 */
        private String requirementName;
    }
}
