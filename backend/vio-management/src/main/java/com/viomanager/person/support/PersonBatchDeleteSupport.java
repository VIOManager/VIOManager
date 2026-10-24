package com.viomanager.person.support;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.MemberErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.person.dto.PersonBatchDeleteDTO;
import com.viomanager.person.entity.Person;
import com.viomanager.person.mapper.PersonMapper;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * M05B 批量删除支撑：整批拒绝口径——任一 id 不可删则全批不动，异常 message 列出
 * 不可删名单与原因（导错重来的场景要的就是全清，半删更乱；2026-10-24 拍板）。
 * 两阶段执行：先全批预检（都存在、都在数据范围内），再逐个套 M05 规则
 * （队长 3003 / 当前登录账号 3004）收集名单，全批可删才动手；
 * 事务边界在 PersonServiceImpl.deleteBatch——任一环节抛错整批回滚，同生共死。
 */
@Component
public class PersonBatchDeleteSupport {

    /** 单次批量上限：防误选全表（与前端 mock 同值，超限 3011） */
    private static final int MAX_BATCH = 200;

    @Resource
    private PersonMapper personMapper;

    @Resource
    private PersonViewerSupport viewerSupport;

    @Resource
    private PersonTeamSupport personTeamSupport;

    @Resource
    private PersonCommandSupport commandSupport;

    /** 预检 + 级联删除：任一处抛 BizException 即整批拒绝（外层事务保证一条都没动过） */
    public void deleteAll(PersonBatchDeleteDTO dto) {
        //0.超上限（3011）：整批拒绝
        if (dto.getIds().size() > MAX_BATCH) {
            throw new BizException(MemberErrorCode.MEMBER_BATCH_LIMIT,
                    "单次批量删除上限 " + MAX_BATCH + " 人");
        }
        CurrentUser user = UserHolder.getUser();
        Long currentMemberId = viewerSupport.currentMemberId(user == null ? null : user.getUserId());
        //1.第一阶段：全部找得到且都在数据范围内（LinkedHashSet 去重保序，重复 id 只删一次）
        Map<Long, Person> targets = new LinkedHashMap<>();
        for (Long id : new LinkedHashSet<>(dto.getIds())) {
            Person person = personMapper.selectById(id);
            if (person == null) {
                throw new BizException(CommonErrorCode.NOT_FOUND, "人员不存在：" + id);
            }
            viewerSupport.assertAdminCanManage(person.getOrgId());
            targets.put(id, person);
        }
        //2.第二阶段：逐个套 M05 规则，有不可删的列名单整批拒绝（有队长用 3003，否则 3004）
        List<String> blocked = new ArrayList<>();
        boolean leaderBlocked = false;
        for (Person person : targets.values()) {
            if (personTeamSupport.isLeaderOfActiveTeam(person.getId())) {
                blocked.add(person.getName() + "：队长，请先转移队长或解散队伍");
                leaderBlocked = true;
            } else if (person.getId().equals(currentMemberId)) {
                blocked.add(person.getName() + "：当前登录账号");
            }
        }
        if (!blocked.isEmpty()) {
            throw new BizException(leaderBlocked ? MemberErrorCode.MEMBER_IS_LEADER
                            : MemberErrorCode.MEMBER_IS_SELF,
                    "整批未删除，不可删——" + String.join("；", blocked));
        }
        //3.全批可删才动手：逐个走 M05 同款级联（逻辑删档案+物理删技能+清队伍关系+禁用账号）
        targets.values().forEach(commandSupport::deletePersonCascade);
    }
}
