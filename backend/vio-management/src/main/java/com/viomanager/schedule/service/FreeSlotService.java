package com.viomanager.schedule.service;

import com.viomanager.schedule.vo.FreeMemberVO;
import com.viomanager.schedule.vo.FreeSlotCellVO;
import java.util.List;

/**
 * 空课查询服务（SC06/SC07，接口清单 §3，仅超管与部门管理员——PRD 明确）。
 * 判定规则同源 FreeSlotCalcSupport（AI 助手复用同一逻辑，权限随当前用户）。
 */
public interface FreeSlotService {

    /** SC06 整周 7×5 概览矩阵：每格 freeCount 与按年级人数分布。 */
    List<FreeSlotCellVO> matrix(Long semesterId, Integer weekNo);

    /** SC07 单格空课名单（按年级分组展示归前端渲染，接口平铺返回）。 */
    List<FreeMemberVO> members(Long semesterId, Integer weekNo, Integer weekday, Integer slotIndex);
}
