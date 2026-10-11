package org.dromara.ai.video.cloud;

import org.dromara.ai.video.exception.VideoTaskException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.SQLException;

/** 与原视频入口共用默认租户约定，仅兼容已确认的单租户数据库结构。 */
public final class VideoCloudTenantResolver {
    private VideoCloudTenantResolver() { }

    public static String resolve(JdbcTemplate jdbc, Long user) {
        if (user == null) throw new VideoTaskException("UNAUTHENTICATED", "当前未登录");
        try {
            var tenants = jdbc.queryForList("SELECT tenant_id FROM sys_user WHERE user_id = ?", String.class, user);
            if (tenants.size() != 1 || tenants.getFirst() == null || tenants.getFirst().isBlank()) {
                throw VideoTaskException.invalidContract("无法确认租户归属");
            }
            return tenants.getFirst();
        } catch (BadSqlGrammarException e) {
            SQLException cause = e.getSQLException();
            if (cause == null || cause.getErrorCode() != 1054 || !"42S22".equals(cause.getSQLState())) throw e;
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id = ?", Integer.class, user);
            if (exists == null || exists != 1) throw VideoTaskException.invalidContract("无法确认当前用户归属");
            return "000000";
        }
    }
}
