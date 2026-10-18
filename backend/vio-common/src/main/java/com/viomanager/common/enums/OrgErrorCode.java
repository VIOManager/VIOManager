package com.viomanager.common.enums;

/**
 * 3xxx 组织管理错误码（规范 §4 段位表归 A 线维护）。
 * 与接口清单 docs/api/A线-基础数据-接口清单.md §1 一致。
 */
public enum OrgErrorCode implements ErrorCode {

    /** 上级组织选了自身或其子孙（O03 改上级防循环引用） */
    ORG_PARENT_INVALID(3006, "上级组织不能选自身或其子孙"),

    /** 组织下有子组织，禁止删除（O04） */
    ORG_HAS_CHILDREN(3007, "组织下有子组织，禁止删除");

    private final int code;
    private final String message;

    OrgErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
