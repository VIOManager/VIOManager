package com.viomanager.org.support;

import com.viomanager.org.vo.CollegeVO;
import java.util.List;

/**
 * 13 学院 39 专业静态映射（O06，PRD §3.2：内置生成、不查库、全站学院下拉统一来源）。
 * ⚠️ 名单为按学校公开信息整理的生成版，学院/专业名称需组长按实际校对一遍后定稿；
 * 改这里只动常量，接口形态不变。
 */
public final class CollegeCatalog {

    private CollegeCatalog() {
    }

    private static final List<CollegeVO> COLLEGES = List.of(
            CollegeVO.of("机械工程学院", List.of("机械设计制造及其自动化", "机械电子工程", "工业设计", "车辆工程")),
            CollegeVO.of("电气与控制工程学院", List.of("电气工程及其自动化", "自动化", "测控技术与仪器", "智能电网信息工程")),
            CollegeVO.of("计算机与信息工程学院", List.of("计算机科学与技术", "软件工程", "物联网工程", "网络工程")),
            CollegeVO.of("电子与信息工程学院", List.of("电子信息工程", "通信工程", "电子信息科学与技术")),
            CollegeVO.of("材料科学与工程学院", List.of("材料科学与工程", "材料成型及控制工程", "焊接技术与工程")),
            CollegeVO.of("安全工程学院", List.of("安全工程", "消防工程")),
            CollegeVO.of("矿业工程学院", List.of("采矿工程", "矿物加工工程")),
            CollegeVO.of("环境与化工学院", List.of("环境工程", "化学工程与工艺")),
            CollegeVO.of("建筑工程学院", List.of("土木工程", "建筑环境与能源应用工程", "建筑学")),
            CollegeVO.of("经济学院", List.of("经济学", "国际经济与贸易")),
            CollegeVO.of("管理学院", List.of("工商管理", "会计学", "财务管理")),
            CollegeVO.of("外国语学院", List.of("英语", "俄语")),
            CollegeVO.of("人文社会科学学院", List.of("汉语言文学", "法学", "社会工作")));

    /** O06 出参：不可变列表直出（无状态静态数据，每次返回同一份） */
    public static List<CollegeVO> list() {
        return COLLEGES;
    }
}
