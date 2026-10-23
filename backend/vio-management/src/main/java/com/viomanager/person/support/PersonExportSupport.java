package com.viomanager.person.support;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.person.dto.PersonQueryDTO;
import com.viomanager.person.entity.Person;
import com.viomanager.person.mapper.PersonMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Excel 写出编排（M06 模板下载 + 契约外 export 导出），与 PersonImportSupport 对称。
 * 导出仅超管可调（controller 注解收口），身份证解密明文进导出文件——该敏感面随 export
 * 接口一起待登记进 A 线清单（需 lzh 拍板）。
 */
@Component
public class PersonExportSupport {

    /** 单次导出行数上限：防一个大导出把服务拖死 */
    private static final int MAX_EXPORT_ROWS = 10000;

    @Resource
    private PersonMapper personMapper;

    @Resource
    private PersonQuerySupport querySupport;

    @Resource
    private PersonSensitiveSupport sensitiveSupport;

    /** M06：14 列模板（表头 + 示例行）写入响应 */
    public void writeTemplate(HttpServletResponse response) {
        PersonExcelWriter.prepareXlsxResponse(response, "人员导入模板.xlsx");
        try {
            PersonExcelWriter.writeTemplate(response.getOutputStream());
        } catch (IOException e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "模板写出失败");
        }
    }

    /** 导出：筛选取数 → 模板同列组装 → 写出（导出→改→再导入可回环） */
    public void writeExport(PersonQueryDTO dto, HttpServletResponse response) {
        //1.按筛选取数，上限 10000 防大导出拖死服务
        QueryWrapper<Person> wrapper = querySupport.buildWrapper(dto);
        List<Person> records = personMapper.selectList(wrapper.last("LIMIT " + MAX_EXPORT_ROWS));
        //2.组装与模板同列的 14 列行
        List<List<String>> rows = buildExportRows(records);
        //3.响应头 + 写出
        String fileName = "人员档案导出_" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        PersonExcelWriter.prepareXlsxResponse(response, fileName);
        try {
            PersonExcelWriter.write(PersonExcelWriter.TEMPLATE_HEADERS, rows, response.getOutputStream());
        } catch (IOException e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "导出文件写出失败");
        }
    }

    /**
     * 导出行组装：与模板同列的 14 列（导出→改→再导入可回环）。
     * 身份证解密明文——export 仅超管可调（controller 注解收口），敏感面与契约外补充接口一并待登记。
     * 证件照/电子签名两列输出占位空串：照片走 M08 文件服务回填，Excel 放不了图片。
     * 2026-10-23 自 PersonQuerySupport 迁入（行数铁律 + 导出行组装本就归导出支撑）。
     */
    private List<List<String>> buildExportRows(List<Person> records) {
        Map<Long, String> orgNames = querySupport.loadOrgNames(
                records.stream().map(Person::getOrgId).filter(Objects::nonNull).distinct().toList());
        List<List<String>> rows = new ArrayList<>(records.size());
        for (Person person : records) {
            rows.add(List.of(
                    StrUtil.nullToEmpty(person.getName()),
                    StrUtil.firstNonBlank(person.getStudentNo(), person.getJobNo(), ""),
                    StrUtil.nullToEmpty(person.getPhone()),
                    StrUtil.nullToEmpty(person.getCollege()),
                    StrUtil.nullToEmpty(person.getMajor()),
                    StrUtil.nullToEmpty(person.getClassName()),
                    StrUtil.nullToEmpty(person.getGender()),
                    StrUtil.nullToEmpty(person.getNation()),
                    StrUtil.nullToEmpty(sensitiveSupport.decryptIdCard(person.getIdCard())),
                    StrUtil.nullToEmpty(person.getEmail()),
                    "", "",
                    orgNames.getOrDefault(person.getOrgId(), ""),
                    StrUtil.nullToEmpty(person.getMemberType())));
        }
        return rows;
    }
}
