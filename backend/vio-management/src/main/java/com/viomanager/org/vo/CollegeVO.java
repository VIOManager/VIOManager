package com.viomanager.org.vo;

import java.util.List;
import lombok.Data;

/**
 * 学院-专业静态映射项（O06 出参，与前端 CollegeItem 一一对应）。
 */
@Data
public class CollegeVO {

    private String college;

    private List<String> majors;

    public static CollegeVO of(String college, List<String> majors) {
        CollegeVO vo = new CollegeVO();
        vo.setCollege(college);
        vo.setMajors(majors);
        return vo;
    }
}
