package com.viomanager.semester.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.Data;

/**
 * 学期新建/编辑入参（S02/S03 共用：都只动基础信息）。
 * status 与 isCurrent 刻意不收——状态流转与当前学期切换只归 S08 执行引擎管，
 * 从入参层断掉"手工把学期改成 ACTIVE/当前"的口子。
 */
@Data
public class SemesterSaveDTO {

    /** 学期名称，不可重复（3008） */
    @NotBlank(message = "学期名称不能为空")
    private String name;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 教学周数，可空默认 20（排班与绩效周次取值范围） */
    private Integer totalWeeks;

    /** 备注 */
    private String remark;
}
