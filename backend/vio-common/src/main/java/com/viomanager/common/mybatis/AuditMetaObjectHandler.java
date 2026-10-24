package com.viomanager.common.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;

/**
 * 审计字段自动填充：insert 补 createdAt/updatedAt/createdBy/updatedBy，update 补 updatedAt/updatedBy。
 * 实体继承 BaseEntity（字段标了 @TableField(fill=...)）即生效，业务代码不碰这四个字段。
 *
 * MetaObject 通俗解释（MyBatis 的类，不是本项目发明）：
 * 它是"把当前正要入库的那个实体包了一层的反射外壳"。执行 mapper.insert(entity) 时，
 * MyBatis-Plus 会回调下面的 insertFill，并把 entity 包成 metaObject 参数递进来；
 * strictInsertFill(metaObject, "createdAt", ...) 的意思就是：
 * "如果这个实体有叫 createdAt 的字段、且当前还是 null，就填上 now"。
 * 它改的就是 entity 本身，所以 insert 完之后 entity.getCreatedAt() 就有值了。
 * 为什么要绕这一层：本类要伺候全部 52 种实体，编译期不可能知道具体是什么类，
 * 只能拿到外壳后按字段名字符串去操作。
 *
 * created_by / updated_by 的 Step4 TODO 已于 2026-10-24 接上：登录态下自动落操作人
 * （系统配置页字典"补充人"列依赖）。无登录态的写入（后台任务、测试直插）保持 NULL，
 * 与列的 NULL 语义一致。
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        Long userId = currentUserId();
        if (userId != null) {
            this.strictInsertFill(metaObject, "createdBy", Long.class, userId);
            this.strictInsertFill(metaObject, "updatedBy", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        Long userId = currentUserId();
        if (userId != null) {
            this.strictUpdateFill(metaObject, "updatedBy", Long.class, userId);
        }
    }

    /** 当前登录用户 id；无登录态（后台任务/定时轮询/测试直插）返回 null 不填 */
    private Long currentUserId() {
        CurrentUser user = UserHolder.getUser();
        return user == null ? null : user.getUserId();
    }
}
