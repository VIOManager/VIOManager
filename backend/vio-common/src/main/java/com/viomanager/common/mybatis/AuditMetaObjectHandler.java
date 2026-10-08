package com.viomanager.common.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;

/**
 * 审计字段自动填充：insert 补 createdAt/updatedAt，update 补 updatedAt。
 * 实体继承 BaseEntity（@TableField(fill=...)）即生效，业务代码不碰这四个字段。
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        // TODO(Step4)：CurrentUser 落地后补 created_by / updated_by（当前无登录态，列允许 NULL）
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        // TODO(Step4)：updated_by
    }
}
