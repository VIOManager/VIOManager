package com.viomanager.person.support;

import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 人员 Excel 写出支撑：模板下载（M06）与导出共用。
 * 用 JDK 自带的 zip 写最小可用的 xlsx：单 sheet、全部内联字符串（不生成 sharedStrings），
 * Excel 与 WPS 均可打开。列定义与 M07 导入的表头定位一一对应——改列必须两头同步。
 */
public final class PersonExcelWriter {

    /**
     * 导入模板 14 列（对齐前端 member.ts 的模板口径；PRD 写 13 列与字段清单 14 项的
     * 矛盾沿用前端已定结论取 14）。证件照/电子签名列读入但忽略值——照片走 M08/M09
     * 文件服务回填，Excel 里放不了图片，列存在只为与纸质表格习惯对齐。
     */
    public static final List<String> TEMPLATE_HEADERS = List.of(
            "姓名", "学号/学工号", "手机号", "学院", "专业", "班级", "性别", "民族",
            "身份证号", "邮箱", "证件照", "电子签名", "所属组织", "类型");

    private static final String SHEET_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml";

    private PersonExcelWriter() {
    }

    /** 统一响应头：xlsx 类型 + 中文文件名双写法（filename 是 ASCII 回退，filename* 是标准 UTF-8） */
    public static void prepareXlsxResponse(HttpServletResponse response, String fileName) {
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
    }

    /** M06 模板下载：表头 + 一行示例（示例行只填关键字段，导入时会被学号查重跳过或替换） */
    public static void writeTemplate(OutputStream out) {
        List<String> example = List.of(
                "张三", "20230001", "13800000000", "计算机学院", "软件工程", "软件2301",
                "MALE", "汉族", "", "", "", "", "示例组织（按名称精确匹配）", "STUDENT");
        write(TEMPLATE_HEADERS, List.of(example), out);
    }

    /**
     * 写出 xlsx 到输出流：headers 为表头（第 1 行），rows 为数据行（从第 2 行起）。
     * 值为 null 视为空单元格；全部用内联字符串，无需共享字符串表。
     */
    public static void write(List<String> headers, List<List<String>> rows, OutputStream out) {
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="%s"/>
                    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>""".formatted(SHEET_CONTENT_TYPE));
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>""");
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets><sheet name="人员档案" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>""");
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>""");
            put(zip, "xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
                    <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
                    <borders count="1"><border/></borders>
                    <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                    <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
                    </styleSheet>""");
            put(zip, "xl/worksheets/sheet1.xml", sheetXml(headers, rows));
        } catch (IOException e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "Excel 文件生成失败");
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String sheetXml(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        writeRow(sb, 1, headers);
        for (int i = 0; i < rows.size(); i++) {
            writeRow(sb, i + 2, rows.get(i));
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static void writeRow(StringBuilder sb, int rowNo, List<String> values) {
        sb.append("<row r=\"").append(rowNo).append("\">");
        for (int c = 0; c < values.size(); c++) {
            String value = values.get(c);
            if (value == null || value.isEmpty()) {
                continue;
            }
            sb.append("<c r=\"").append(colLetter(c)).append(rowNo)
                    .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escapeXml(value)).append("</t></is></c>");
        }
        sb.append("</row>");
    }

    /** 0 → A，1 → B，…，26 → AA（人员档案列数用不到三位，防御性写全） */
    private static String colLetter(int index) {
        StringBuilder sb = new StringBuilder();
        int remaining = index;
        do {
            sb.insert(0, (char) ('A' + remaining % 26));
            remaining = remaining / 26 - 1;
        } while (remaining >= 0);
        return sb.toString();
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
