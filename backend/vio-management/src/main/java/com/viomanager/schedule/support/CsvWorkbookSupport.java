package com.viomanager.schedule.support;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * CSV → POI 工作簿适配（教务导出两种格式：xlsx 与 CSV，解析链统一走 POI）。
 * RFC 4180 口径：引号包裹字段可含逗号/换行、双写引号转义；UTF-8（去 BOM）优先、
 * 严格解码失败回退 GBK（教务老系统常见）；上限 20 万行防误传大文件。
 */
@Component
public class CsvWorkbookSupport {

    /** CSV 记录数上限（真实全校课表 4 千行，量级足够）。 */
    private static final int MAX_RECORDS = 200_000;

    /** 把 CSV 字节转成单 sheet 的 XSSFWorkbook（表头识别与列解析交给下游）。 */
    public XSSFWorkbook toWorkbook(byte[] bytes) {
        List<String[]> records = parse(decode(bytes));
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("导入数据");
        for (int r = 0; r < records.size(); r++) {
            Row row = sheet.createRow(r);
            String[] record = records.get(r);
            for (int c = 0; c < record.length; c++) {
                row.createCell(c).setCellValue(record[c]);
            }
        }
        return wb;
    }

    /** 严格 UTF-8（去 BOM）失败回退 GBK。 */
    String decode(byte[] bytes) {
        int offset = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(java.nio.ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
        } catch (Exception e) {
            return new String(bytes, offset, bytes.length - offset, Charset.forName("GBK"));
        }
    }

    /** 状态机解析：引号内逗号/换行属于字段内容，双写引号转义。 */
    List<String[]> parse(String text) {
        List<String[]> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < length && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }
            if (ch == '"' && field.length() == 0 && !fieldStarted) {
                inQuotes = true;
                fieldStarted = true;
            } else if (ch == ',') {
                fields.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
            } else if (ch == '\r' || ch == '\n') {
                if (ch == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') {
                    i++;
                }
                fields.add(field.toString());
                if (fields.size() > 1 || !fields.get(0).isBlank()) {
                    records.add(fields.toArray(new String[0]));
                }
                fields = new ArrayList<>();
                field.setLength(0);
                fieldStarted = false;
                if (records.size() > MAX_RECORDS) {
                    throw new IllegalArgumentException("CSV 行数超过上限 " + MAX_RECORDS);
                }
            } else {
                field.append(ch);
                fieldStarted = true;
            }
        }
        if (field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            if (fields.size() > 1 || !fields.get(0).isBlank()) {
                records.add(fields.toArray(new String[0]));
            }
        }
        return records;
    }
}
