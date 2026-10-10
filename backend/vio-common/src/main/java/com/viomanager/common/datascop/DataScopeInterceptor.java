package com.viomanager.common.datascop;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 数据范围拦截器：把"按角色看得见多少数据"从业务代码里拿走，SQL 层统一注入（规范 §5.2）。
 * 规则：
 *  - 无登录态（系统内部查询，如登录、token 校验）→ 放行不动 SQL
 *  - @DataScope 声明为 SELF / ALL → 放行不动 SQL
 *  - 声明为 ORG：超管放行；部门管理员注入 orgColumn IN (本部门子树)；学生拒绝
 *  - 没有声明：超管/部门管理员放行不注入（防止对没有组织列的表注入出 SQL 错误）；
 *    学生一律拒绝——默认拒绝是防泄露底线：某条线忘写"本人参与"条件，最多是报错，绝不会泄露全量
 */
@Slf4j
public class DataScopeInterceptor implements InnerInterceptor {

    /** 注解解析缓存：mapper 方法全名 → 注解（包一层 Optional 才能缓存"未标注"这个结果） */
    private final Map<String, Optional<DataScope>> annotationCache = new ConcurrentHashMap<>();

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                            ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        //1.无登录态 = 系统内部查询（登录流程、token 校验），放行
        CurrentUser user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        //2.解析 @DataScope（方法上的优先，mapper 接口上的兜底）
        DataScope dataScope = findAnnotation(ms);
        boolean isStudent = "STUDENT".equals(user.getRole());
        boolean isDeptAdmin = "DEPT_ADMIN".equals(user.getRole());
        //3.学生默认拒绝：没声明、或只声明了 ORG 的查询一律拦下
        if (isStudent && (dataScope == null || dataScope.scope() == DataScope.Scope.ORG)) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "该查询未声明学生数据范围，默认拒绝");
        }
        //4.只有"部门管理员 + ORG 声明"才注入子树过滤，其余到此为止
        if (!isDeptAdmin || dataScope == null || dataScope.scope() != DataScope.Scope.ORG) {
            return;
        }
        //5.部门管理员必须配了组织根，没配就是脏数据，拒绝而非放行
        if (user.getOrgId() == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "部门管理员未配置所属组织");
        }
        //6.算子树组织 ID 列表
        List<Long> subtreeOrgIds = querySubtreeOrgIds(executor, user.getOrgId());
        if (subtreeOrgIds.isEmpty()) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "部门管理员的组织不存在或已删除");
        }
        //7.改写 SQL：追加 AND 组织列 IN (子树ID们)
        String newSql = appendOrgCondition(boundSql.getSql(), dataScope.orgColumn(), subtreeOrgIds);
        PluginUtils.mpBoundSql(boundSql).sql(newSql);
    }

    /**
     * 找 @DataScope：先看 mapper 方法，再看 mapper 接口，都没有返回 null。
     */
    private DataScope findAnnotation(MappedStatement ms) {
        //1.查缓存
        String statementId = ms.getId();
        Optional<DataScope> cached = annotationCache.get(statementId);
        if (cached != null) {
            return cached.orElse(null);
        }
        //2.反射解析（statementId 形如 com.viomanager.xxx.XxxMapper.methodName）
        DataScope found = null;
        try {
            int lastDot = statementId.lastIndexOf('.');
            String mapperClassName = statementId.substring(0, lastDot);
            String methodName = statementId.substring(lastDot + 1);
            Class<?> mapperClass = Class.forName(mapperClassName);
            //2.1方法上的注解优先
            for (Method method : mapperClass.getMethods()) {
                if (method.getName().equals(methodName) && method.getAnnotation(DataScope.class) != null) {
                    found = method.getAnnotation(DataScope.class);
                    break;
                }
            }
            //2.2接口上的注解兜底
            if (found == null) {
                found = mapperClass.getAnnotation(DataScope.class);
            }
        } catch (ClassNotFoundException e) {
            // 找不到类说明不是本项目 mapper（理论不发生），按未标注处理
            log.warn("数据范围注解解析失败：{}", statementId);
        }
        //3.入缓存
        annotationCache.put(statementId, Optional.ofNullable(found));
        return found;
    }

    /**
     * 查组织的子树 ID 列表（含自己）。
     * ⚠️ 难点：这里必须用原始 JDBC 直接查，不能再走 MyBatis——
     * 走 MyBatis 会再次触发本拦截器，变成"查子树又被自己拦"的递归。
     * 用当前事务的 Connection 查，不额外占连接池连接。
     */
    private List<Long> querySubtreeOrgIds(Executor executor, Long orgId) throws SQLException {
        Connection connection = executor.getTransaction().getConnection();
        //1.先查本组织的 path（形如 /0/1/12/，含自身）
        String path;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT path FROM sys_org WHERE id = ? AND deleted = 0")) {
            ps.setLong(1, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new ArrayList<>();
                }
                path = rs.getString(1);
            }
        }
        if (path == null || path.isEmpty()) {
            return new ArrayList<>();
        }
        //2.前缀匹配查子树。path 以斜杠结尾：'/0/1/12/%' 匹配自己和子孙，不会误配 '/0/1/120/'
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM sys_org WHERE path LIKE CONCAT(?, '%') AND deleted = 0")) {
            ps.setString(1, path);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    /**
     * 用 JSqlParser 往 SQL 追加 AND 组织列 IN (...)。
     * 解析失败直接抛错而不是放行——宁可这次查询失败，也不能让部门管理员看到全量数据（fail-closed）。
     */
    private String appendOrgCondition(String sql, String orgColumn, List<Long> orgIds) {
        PlainSelect select;
        try {
            select = (PlainSelect) CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            log.error("数据范围注入时 SQL 解析失败，按 fail-closed 拒绝：{}", sql);
            throw new BizException(CommonErrorCode.FORBIDDEN, "数据范围过滤失败，请联系管理员");
        }
        //1.拼 IN 列表（ParenthesedExpressionList 会渲染成 (1, 2, 3)，括号不能少）
        List<LongValue> idValues = new ArrayList<>();
        for (Long orgId : orgIds) {
            idValues.add(new LongValue(orgId));
        }
        InExpression inExpression = new InExpression();
        inExpression.setLeftExpression(new Column(orgColumn));
        inExpression.setRightExpression(new ParenthesedExpressionList<>(idValues));
        //2.追加到 where：有 where 就 AND 上，没有就直接当 where
        Expression where = select.getWhere();
        if (where == null) {
            select.setWhere(inExpression);
        } else {
            select.setWhere(new AndExpression(where, inExpression));
        }
        return select.toString();
    }
}
