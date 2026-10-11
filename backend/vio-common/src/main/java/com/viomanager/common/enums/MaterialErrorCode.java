package com.viomanager.common.enums;

/**
 * 5xxx 材料与成果错误码（规范 §4 段位表归 C 线维护）。
 * 码值与含义严格照 C 线接口清单《C线-材料成果-接口清单.md》第 1 章「错误码初始表」。
 *
 * ⚠️ 本文件位于 common.enums 属**公共组件**：按规范 §7.6，开 PR 前须在群里报备。
 *
 * ⚠️ 已知冲突（待组长拍板，见 C 线接口清单「待确认」）：
 * 前端自拟清单把 5002 用作「已过截止时间」，而本文档 5002 是「文件超过大小上限」。
 * 本次实现以本文档为准；若组长改判，只改本枚举 + 前端 labels 即可。
 */
public enum MaterialErrorCode implements ErrorCode {

    /** 文件格式不在该要求的 allowedFormats 白名单内（校验序第 1 步）。 */
    FILE_FORMAT_NOT_ALLOWED(5001, "文件格式不允许"),

    /** 文件超过该要求的 maxSizeMb 上限（校验序第 2 步）。 */
    FILE_TOO_LARGE(5002, "文件超过大小上限"),

    /** 提交人不是该赛队的在队成员，禁止操作（校验序第 3 步）。 */
    NOT_TEAM_MEMBER(5003, "非本队在队成员，禁止操作"),

    /** 材料要求所属比赛与赛队所属比赛不一致（校验序第 4 步）。 */
    CONTEST_MISMATCH(5004, "材料要求与赛队所属比赛不匹配"),

    /** 该要求下已有提交记录，禁止删除（MR03）。 */
    REQUIREMENT_HAS_SUBMISSION(5005, "该要求已有提交记录，禁止删除"),

    /** 该任务下任一要求已有提交记录，禁止删除任务（MT04）。 */
    TASK_HAS_SUBMISSION(5006, "任务下存在已提交的要求，禁止删除"),

    /** 成果佐证材料仅当前队长可上传/覆盖（A06）。 */
    EVIDENCE_LEADER_ONLY(5007, "仅当前队长可上传佐证材料"),

    /** 队伍存在未通过（待审批/已驳回）成果时，禁止导出获奖清单（A07）。 */
    EXPORT_REQUIRES_APPROVED(5008, "仅审批通过的成果可导出"),

    /** 无该文件下载权限（MS04）。 */
    FILE_DOWNLOAD_FORBIDDEN(5009, "无该文件下载权限");

    private final int code;
    private final String message;

    MaterialErrorCode(int code, String message) {
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
