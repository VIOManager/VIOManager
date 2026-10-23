package com.viomanager.person.support;

import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人员 Excel 导入读取支撑：解析 .xlsx 为按行的单元格文本（写出在 PersonExcelWriter）。
 * 工程未引 POI / EasyExcel（父 pom 锁定的依赖里只有 hutool-core），也约定不改其他模块，
 * 因此用 JDK 自带的 zip + StAX 实现最小可用的 xlsx 读：
 * 单 sheet（第一个 worksheet），支持共享字符串 / 内联字符串 / 数字单元格。
 * 行模型为 List&lt;String&gt;（单元格值全部按文本取），列的含义由表头行决定。
 */
public final class PersonExcelSupport {

    /** 单文件字节上限：先于整包读内存拦截超大文件，防 OOM（5000 行 xlsx 远小于此值） */
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private PersonExcelSupport() {
    }

    /**
     * 读取上传的 .xlsx，返回按行拆分的单元格文本：第一行为表头，其余为数据行。
     * 文件为空 / 不是 .xlsx / 解析失败时抛 1001，由全局异常处理器统一转 Result。
     */
    public static List<List<String>> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "请选择要导入的 Excel 文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "Excel 文件过大（上限 5MB），请分批导入");
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
        String cellType = null;
        StringBuilder cellText = null;
        boolean inValue = false;
        boolean inInlineText = false;
        int currentCol = -1;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "row" -> currentRow = new TreeMap<>();
                    case "c" -> {
                        cellType = reader.getAttributeValue(null, "t");
                        cellText = new StringBuilder();
                        currentCol = colIndexOf(reader.getAttributeValue(null, "r"));
                    }
                    case "v" -> inValue = true;
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
        if (ref == null) {
            return -1;
        }
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

    /** 关 DTD 与外部实体：xlsx 是不受信输入，防 XML 实体注入（XXE） */
    private static XMLInputFactory newFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }
}
