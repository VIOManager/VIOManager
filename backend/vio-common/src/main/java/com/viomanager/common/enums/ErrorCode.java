package com.viomanager.common.enums;

/**
 * 错误码契约：所有错误码枚举实现本接口，BizException 与 Result 只认它。
 * 各段位归属见《全局接口与开发规范》§4，新增码先登记 Apifox 再落枚举。
 */
public interface ErrorCode {

    /** 业务错误码（0 为成功，不在错误码枚举中出现）。 */
    int getCode();

    /** 默认文案，前端可直接展示；需要更具体说明时可覆盖。 */
    String getMessage();
}
