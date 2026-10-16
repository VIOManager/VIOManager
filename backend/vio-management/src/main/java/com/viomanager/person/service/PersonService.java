package com.viomanager.person.service;

import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.person.dto.PersonCreateDTO;
import com.viomanager.person.dto.PersonQueryDTO;
import com.viomanager.person.dto.PersonUpdateDTO;
import com.viomanager.person.dto.PersonVO;
import com.viomanager.person.support.PersonExcelSupport;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人员档案服务：超管后台维护人员档案的业务核心。
 * 所有复杂逻辑（学号查重、身份证加解密、敏感字段按角色脱敏、Excel 解析组装）都在实现类里，
 * controller 只做参数接收与转发，不写业务。
 */
public interface PersonService {

    /**
     * P01 新增人员：学号/学工号查重 → 身份证加密入库 → 技能标签落 member_skill。返回新档案 VO。
     */
    Result<PersonVO> create(PersonCreateDTO dto);

    /**
     * P02 编辑人员：存在性校验 → 部分更新（null 不改）→ 技能标签整组替换。
     */
    void update(Long id, PersonUpdateDTO dto);

    /**
     * P03 删除人员：存在性校验 → 逻辑删除档案与技能标签（附件留在文件模块，不物理清理）。
     */
    void delete(Long id);

    /**
     * P04 分页查询：按筛选条件分页，出参敏感字段按当前登录角色脱敏。
     */
    Result<PageResult<PersonVO>> page(PersonQueryDTO dto);

    /**
     * P05 人员详情：单条完整档案，同样按角色脱敏。
     */
    Result<PersonVO> detail(Long id);

    /**
     * P06 Excel 批量导入：逐行解析校验，合法行入库、非法行记错误，返回逐行结果。
     */
    Result<PersonExcelSupport.ImportResult> importExcel(MultipartFile file);

    /**
     * P07 Excel 导出：按筛选条件查库组装 xlsx 写入响应（仅超管可调，导出完整敏感信息）。
     */
    void export(PersonQueryDTO dto, HttpServletResponse response);

    /**
     * P08 证件照上传绑定：复用文件模块上传图片拿 fileId，写入人员档案 photo_file_id。
     */
    void bindPhoto(Long id, MultipartFile file);
}
