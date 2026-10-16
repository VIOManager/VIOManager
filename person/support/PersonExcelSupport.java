package com.viomanager.person.support;

import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人员 Excel 导入导出支撑：.xlsx 读写。
 * 工程未引 POI / EasyExcel（父 pom 锁定的依赖里只有 hutool-core），也约定不改其他模块，
 * 因此用 JDK 自带的 zip + StAX 实现最小可用的 xlsx 读写：
 *  - 读：单 sheet（第一个 worksheet），支持共享字符串 / 内联字符串 / 数字单元格；
 *  - 写：单 sheet，全部用 inlineStr（不生成 sharedStrings），Excel 与 WPS 均可打开。
 * 行模型为 List&lt;String&gt;（单元格值全部按文本取），列的含义由表头行决定。
 */
public final class PersonExcelSupport {

    /** 导入结果出参：总行数、成功行数、逐行错误（"第 N 行：xxx"，N 为 Excel 里的实际行号，含表头） */
    public record ImportResult(int totalRows, int successRows, List<String> errors) {
    }

    private static final String SHEET_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml";

    private PersonExcelSupport() {
    }

    // ------------------------------------------------------------------ 读

    /**
     * 读取上传的 .xlsx，返回按行拆分的单元格文本：第一行为表头，其余为数据行。
     * 文件为空 / 不是 .xlsx / 解析失败时抛 1001，由全局异常处理器统一转 Result。
     */
    public static List<List<String>> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "请选择要导入的 Excel 文件");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".xlsx")) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "仅支持 .xlsx 格式文件");
        }
        byte[] sharedXml = null;
        byte[] sheetXml = null;
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("xl/sharedStrings.xml".equals(name)) {
                    sharedXml = zip.readAllBytes();
                } else if (sheetXml == null && name.startsWith("xl/worksheets/") && name.endsWith(".xml")) {
                    sheetXml = zip.readAllBytes();
                }
            }
        } catch (IOException e) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "Excel 文件读取失败，请确认上传的是有效的 .xlsx 文件");
        }
        if (sheetXml == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "Excel 中没有工作表");
        }
        try {
            List<String> shared = sharedXml == null ? List.of() : parseSharedStrings(sharedXml);
            return parseSheet(sheetXml, shared);
        } catch (XMLStreamException e) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "Excel 文件解析失败，请确认上传的是有效的 .xlsx 文件");
        }
    }

    /** 解析 xl/sharedStrings.xml：把每个 &lt;si&gt; 内所有 &lt;t&gt; 文本拼接为一项（兼容富文本分段） */
    private static List<String> parseSharedStrings(byte[] xml) throws XMLStreamException {
        XMLStreamReader reader = newFactory().createXMLStreamReader(
                new ByteArrayInputStream(xml), StandardCharsets.UTF_8.name());
        List<String> shared = new ArrayList<>();
        StringBuilder current = null;
        boolean inText = false;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "si" -> current = new StringBuilder();
                    case "t" -> inText = true;
                    default -> { }
                }
            } else if (event == XMLStreamConstants.CHARACTERS) {
                if (inText && current != null) {
                    current.append(reader.getText());
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "t" -> inText = false;
                    case "si" -> {
                        shared.add(current == null ? "" : current.toString());
                        current = null;
                    }
                    default -> { }
                }
            }
        }
        reader.close();
        return shared;
    }

    /** 解析 worksheet：按单元格引用（如 C5）定位列，空单元格在行内补 null 保持列对齐 */
    private static List<List<String>> parseSheet(byte[] xml, List<String> shared) throws XMLStreamException {
        XMLStreamReader reader = newFactory().createXMLStreamReader(
                new ByteArrayInputStream(xml), StandardCharsets.UTF_8.name());
        List<List<String>> rows = new ArrayList<>();
        TreeMap<Integer, String> currentRow = null;
        int lastRowIndex = 0;
        int currentCol = -1;
        String cellType = null;
        StringBuilder cellText = null;
        boolean inValue = false;
        boolean inInlineText = false;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "row" -> {
                        currentRow = new TreeMap<>();
                        String attr = reader.getAttributeValue(null, "r");
                        lastRowIndex = attr != null ? Integer.parseInt(attr) : lastRowIndex + 1;
                    }
                    case "c" -> {
                        cellType = reader.getAttributeValue(null, "t");
                        String ref = reader.getAttributeValue(null, "r");
                        currentCol = ref != null ? colIndexOf(ref) : currentCol + 1;
                        cellText = new StringBuilder();
                    }
                    case "v" -> {
                        inValue = true;
                        cellText.setLength(0);
                    }
                    case "is" -> inInlineText = true;
                    case "t" -> {
                        if (inInlineText) {
                            inValue = true;
                        }
                    }
                    default -> { }
                }
            } else if (event == XMLStreamConstants.CHARACTERS) {
                if (inValue && cellText != null) {
                    cellText.append(reader.getText());
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "v" -> inValue = false;
                    case "t" -> {
                        if (inInlineText) {
                            inValue = false;
                        }
                    }
                    case "is" -> inInlineText = false;
                    case "c" -> {
                        String value = resolveCell(cellType, cellText == null ? "" : cellText.toString(), shared);
                        if (currentRow != null && !value.isEmpty()) {
                            currentRow.put(currentCol, value);
                        }
                        cellType = null;
                        cellText = null;
                        inValue = false;
                    }
                    case "row" -> {
                        if (currentRow != null) {
                            List<String> row = new ArrayList<>();
                            int lastCol = currentRow.isEmpty() ? -1 : currentRow.lastKey();
                            for (int i = 0; i <= lastCol; i++) {
                                row.add(currentRow.get(i));
                            }
                            rows.add(row);
                            currentRow = null;
                            currentCol = -1;
                        }
                    }
                    default -> { }
                }
            }
        }
        reader.close();
        return rows;
    }

    /** 单元格取值：s=共享字符串 / inlineStr·str=文本 / b=布尔 / 其余（数字）原样返回 */
    private static String resolveCell(String type, String text, List<String> shared) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return switch (type == null ? "" : type) {
            case "s" -> {
                try {
                    yield shared.get(Integer.parseInt(trimmed));
                } catch (RuntimeException e) {
                    yield trimmed;
                }
            }
            case "b" -> "1".equals(trimmed) ? "TRUE" : "FALSE";
            default -> trimmed;
        };
    }

    /** 列引用字母（A1 的 "A"）→ 0 起下标 */
    private static int colIndexOf(String ref) {
        int index = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                index = index * 26 + (c - 'A' + 1);
            } else if (c >= 'a' && c <= 'z') {
                index = index * 26 + (c - 'a' + 1);
            } else {
                break;
            }
        }
        return index - 1;
    }

    // ------------------------------------------------------------------ 写

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

    /** 关 DTD 与外部实体：xlsx 是不受信输入，防 XML 实体注入（XXE） */
    private static XMLInputFactory newFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }
}
