package com.viomanager.semester.service;

import com.viomanager.semester.dto.SwitchTaskCreateDTO;
import com.viomanager.semester.vo.SwitchTaskVO;
import java.util.List;

/**
 * 学期切换任务服务（A 线契约 S05-S08，仅超管）。
 * S05 发起（密码二次确认 + 30 分钟窗口 + 影响快照）、S06 列表、
 * S07 窗口内取消、S08 执行（乐观锁占位 → 维护锁 → 引擎搬运 → 收尾回写）。
 */
public interface SwitchTaskService {

    /** S05 发起切换：返回任务 ID */
    Long create(SwitchTaskCreateDTO dto);

    /** S06 任务列表（新在前，含快照与执行结果计数） */
    List<SwitchTaskVO> list();

    /** S07 取消：仅 PENDING_EXECUTE 可取消（条件更新防并发） */
    void cancel(Long id);

    /** S08 执行：窗口校验 → 乐观锁占位 RUNNING → 维护锁 → 引擎 → 成败收尾 */
    void execute(Long id);
}
