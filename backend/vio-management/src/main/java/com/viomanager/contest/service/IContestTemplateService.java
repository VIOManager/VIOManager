package com.viomanager.contest.service;

import com.viomanager.common.result.Result;
import com.viomanager.contest.dto.TemplateCreateDTO;
import com.viomanager.contest.vo.TemplateVO;
import java.util.List;

/**
 * 比赛模板服务（T01–T05）。
 * 模板为全平台公共数据，管理端可 CRUD；被比赛引用时禁止删除（4012）。
 * defaultRules 变更不影响已创建比赛（比赛创建时快照合并）。
 */
public interface IContestTemplateService {

    /** T01 列表（含启用状态、类别筛选 category） */
    Result<List<TemplateVO>> listTemplates(String category);

    /** T02 创建 */
    Result<TemplateVO> createTemplate(TemplateCreateDTO dto);

    /** T03 详情 */
    Result<TemplateVO> getTemplate(Long id);

    /** T04 编辑（默认规则变更不影响已创建比赛） */
    Result<TemplateVO> updateTemplate(Long id, TemplateCreateDTO dto);

    /** T05 删除（被比赛引用拒绝 4012） */
    Result<Void> deleteTemplate(Long id);
}
