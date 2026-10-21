package com.viomanager.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.viomanager.common.datascop.DataScopeInterceptor;
import com.viomanager.common.mybatis.AuditMetaObjectHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件装配：数据范围 + 分页 + 乐观锁，审计填充见 AuditMetaObjectHandler。
 * 组长规矩：配置类一律放 config 包。
 * 数据范围开关（论坛独立库方案 §二.1）：bean 无条件注册保证分页/乐观锁恒在，
 * 数据范围拦截器按 vio.mybatis.data-scope.enabled 决定是否 add——管理系统不写此键
 * （默认 true 行为零变化）；论坛服务置 false（论坛无组织数据范围，且学生默认拒绝
 * 规则会拦死论坛全部查询）。DataScopeInterceptor 无 @Component 只在此手工 new，
 * 不与 starter 自动装配打架。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            @Value("${vio.mybatis.data-scope.enabled:true}") boolean dataScopeEnabled) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        if (dataScopeEnabled) {
            // 数据范围：部门管理员子树注入 / 学生默认拒绝（规范 §5.2）
            // ⚠️ 冲突：拦截器顺序有讲究——数据范围等改写 SQL 的插件必须加在分页之前
            // （先补 where 再分页，否则分页统计的 total 不带过滤条件）。
            interceptor.addInnerInterceptor(new DataScopeInterceptor());
        }
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
