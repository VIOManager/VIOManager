package com.viomanager.person.dto;

import lombok.Data;

/**
 * 人员分页查询入参（P04 列表 / P07 导出共用）：管理员在后台列表页的筛选条件。
 * service 层用它拼接 QueryWrapper 做模糊搜索与筛选；page/size 遵循规范 §2.3
 * （默认 page=1、size=20，size 上限 100 由 service 校验截断）。
 */
@Data
public class PersonQueryDTO {

    /** 姓名（模糊搜索） */
    private String name;

    /** 学号（模糊搜索） */
    private String studentNo;

    /** 学院（模糊搜索） */
    private String college;

    /** 专业（模糊搜索） */
    private String major;

    /** 人员类型：STUDENT / TEACHER / OTHER（精确筛选） */
    private String memberType;

    /** 人员状态：1 在册 0 离册（精确筛选） */
    private Integer status;

    /** 所属组织 ID（精确筛选） */
    private Long orgId;

    /** 页码，从 1 开始 */
    private Integer page = 1;

    /** 每页条数，默认 20，上限 100 */
    private Integer size = 20;
}
