package org.dromara.ai.image.service;

import org.dromara.ai.image.exception.ImageTaskException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;

/** 沿用图像模块的单租户约定；只有实际用户表没有租户列时才使用默认租户。 */
@Service
@ConditionalOnProperty(prefix = "image", name = "enabled", havingValue = "true")
public class ImageInspirationTenantResolver {
    private static final String DEFAULT_TENANT = "000000";
    private final JdbcTemplate jdbc;

    public ImageInspirationTenantResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 账号来自登录态，不能接受客户端指定；数据库错误不能触发默认租户回落。 */
    public String resolve(long user) {
        if (user <= 0) throw unauthenticated();
        // 零行查询只读取当前 SQL 实际解析的表结构，不读取用户资料，也避免 JDBC catalog/schema 匹配歧义。
        return jdbc.execute((ConnectionCallback<String>) connection -> {
            boolean hasUser = false, hasTenant = false;
            try (var statement = connection.prepareStatement("SELECT * FROM sys_user WHERE 1 = 0");
                 var rows = statement.executeQuery()) {
                ResultSetMetaData columns = rows.getMetaData();
                for (int i = 1; i <= columns.getColumnCount(); i++) {
                    hasUser |= "user_id".equalsIgnoreCase(columns.getColumnName(i));
                    hasTenant |= "tenant_id".equalsIgnoreCase(columns.getColumnName(i));
                }
            }
            if (!hasUser) throw unauthenticated();
            String sql = hasTenant ? "SELECT tenant_id FROM sys_user WHERE user_id = ?"
                : "SELECT user_id FROM sys_user WHERE user_id = ?";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, user);
                try (var account = statement.executeQuery()) {
                    if (!account.next()) throw unauthenticated();
                    String tenant = hasTenant ? account.getString(1) : DEFAULT_TENANT;
                    if (tenant == null || tenant.isBlank() || account.next()) throw unauthenticated();
                    return tenant;
                }
            }
        });
    }

    private static ImageTaskException unauthenticated() {
        return new ImageTaskException("UNAUTHENTICATED", "无法确认当前账号归属");
    }
}
