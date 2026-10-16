package com.viomanager.person.dto;

/**
 * M10 出参：完整身份证号。
 * ⚠️ 敏感：本接口每次调用都写审计日志（谁在什么时间看了谁的身份证）；
 * 出参只有这一个字段，防止顺手多带。
 */
public record PersonIdCardVO(String idCard) {
}
