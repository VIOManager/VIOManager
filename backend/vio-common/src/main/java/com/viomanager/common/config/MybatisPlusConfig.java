package com.viomanager.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.viomanager.common.mybatis.AuditMetaObjectHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件装配：分页 + 乐观锁，审计填充见 AuditMetaObjectHandler。
 * 组长规矩：配置类一律放 config 包。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * ⚠️ 冲突：拦截器顺序有讲究——数据范围等改写 SQL 的插件必须加在分页之前
     * （先补 where 再分页，否则分页统计的 total 不带过滤条件）。
     * Step 4 数据范围插件在此处按序插入，分页永远保持最后。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页：规范 §2.3 分页约定的机制基础（page/size，size 上限由业务层校验）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 乐观锁：实体加 @Version 即生效，是 1007 并发修改码的机制基础
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public AuditMetaObjectHandler auditMetaObjectHandler() {
        return new AuditMetaObjectHandler();
    }
}
