package com.viomanager.person.service;

import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.person.dto.MemberOptionVO;
import com.viomanager.person.dto.PersonAccountDTO;
import com.viomanager.person.dto.PersonBatchDeleteDTO;
import com.viomanager.person.dto.PersonCreateDTO;
import com.viomanager.person.dto.PersonFileBindDTO;
import com.viomanager.person.dto.PersonIdCardVO;
import com.viomanager.person.dto.PersonImportVO;
import com.viomanager.person.dto.PersonQueryDTO;
import com.viomanager.person.dto.PersonUpdateDTO;
import com.viomanager.person.dto.PersonVO;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人员档案服务（A 线契约 M01–M14 + export 补充接口）。
 * controller 只收参数转发；复杂逻辑拆在 support 包：
 * 视角判定 PersonViewerSupport、查询裁剪 PersonQuerySupport、写侧动作 PersonCommandSupport、
 * 导入 PersonImportSupport、Excel 读写 PersonExcelSupport / PersonExcelWriter。
 */
public interface PersonService {

    /** M01 分页列表：部门管理员服务端叠加组织子树过滤；列表永不返回身份证/证件照/邮箱。 */
    Result<PageResult<PersonVO>> page(PersonQueryDTO dto);

    /** M14 受限候选搜索（队长流程用）：任意登录角色，仅目录级七字段（id/姓名/类型/学号/工号/组织），
     *  无任何敏感列；B 线队长端加载预填成员与指导教师候选专用（原先直调 M01 必 403）。 */
    Result<PageResult<MemberOptionVO>> options(String keyword, String memberType, Integer page, Integer size);

    /** M02 详情：任意登录用户，出参按四档视角裁剪（全量/队长大部分/受限）。 */
    Result<PersonVO> detail(Long id);

    /** M03 新建：学号/学工号查重（3001）、身份证加密、技能校验落库，返回新档案。 */
    Result<PersonVO> create(PersonCreateDTO dto);

    /** M04 编辑：部分更新（null 不改），学号不收（不可修改）。 */
    void update(Long id, PersonUpdateDTO dto);

    /** M05 删除：队长 3003 / 本人账号 3004 / 清队伍关系 / 禁用其账号。 */
    void delete(Long id);

    /** M05B 批量删除（DELETE /api/members，body 传 ids）：整批拒绝——任一 id 不可删
     *  （队长 3003 / 本人 3004 / 越权 1003 / 不存在 1004）全批不动，message 列出名单与原因；
     *  单次上限 200（3011）；重复 id 去重只删一次。 */
    void deleteBatch(PersonBatchDeleteDTO dto);

    /** M06 导入模板下载：14 列 xlsx 写入响应。 */
    void importTemplate(HttpServletResponse response);

    /** M07 Excel 导入：组织按名称匹配，学号已存在跳过，返回逐行结果。 */
    Result<PersonImportVO> importExcel(MultipartFile file);

    /** M08 证件照回填：fileId 须为当前登录人上传的干净图片。 */
    void bindPhoto(Long id, PersonFileBindDTO dto);

    /** M09 电子签名回填：口径同 M08。 */
    void bindSignature(Long id, PersonFileBindDTO dto);

    /** M10 完整身份证号：视角不足抛 3010；controller 已挂审计。 */
    Result<PersonIdCardVO> idCard(Long id);

    /** M11a 证件照下载：权限同 M10，经文件服务病毒闸门流式回写。 */
    void downloadPhoto(Long id, HttpServletResponse response);

    /** M11b 电子签名下载：口径同 M11a。 */
    void downloadSignature(Long id, HttpServletResponse response);

    /** M12 技能整组替换：本人或管理员，提交项必须是 SKILL 字典可用项的显示名 itemLabel（2026-10-24 拍板全链路 label 语系）。 */
    void updateSkills(Long id, List<String> skills);

    /** M13 绑定账号：登录名缺省取学工号再退学号；初始密码 abc123。 */
    void bindAccount(Long id, PersonAccountDTO dto);

    /** M13 修改账号：改登录名 / 重置密码（旧 token 失效）。 */
    void changeAccount(Long id, PersonAccountDTO dto);

    /** 导出（契约外补充）：与模板同列，组织输出名称，可回环再导入。 */
    void export(PersonQueryDTO dto, HttpServletResponse response);
}
