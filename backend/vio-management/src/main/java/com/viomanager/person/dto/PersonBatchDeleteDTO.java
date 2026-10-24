package com.viomanager.person.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

/**
 * M05B 批量删除入参：人员档案 ID 列表（DELETE /api/members，body 携带）。
 * 整批拒绝口径——任一 id 不可删则全批不动；服务端去重，重复 id 只删一次。
 */
@Data
public class PersonBatchDeleteDTO {

    /** 待删除的档案 ID（1–200 个，超上限 3011） */
    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;
}
