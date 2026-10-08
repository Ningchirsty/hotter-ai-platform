package org.dromara.ai.image.service;

import org.dromara.ai.image.exception.ImageTaskException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ImageInspirationTenantResolverTest {
    private JdbcTemplate database() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return new JdbcTemplate(source);
    }

    @Test void productionSingleTenantSchemaRequiresAnExistingAccount() {
        JdbcTemplate jdbc = database();
        jdbc.execute("CREATE TABLE sys_user (user_id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO sys_user VALUES (?)", 123L);
        ImageInspirationTenantResolver resolver = new ImageInspirationTenantResolver(jdbc);
        assertEquals("000000", resolver.resolve(123L));
        assertThrows(ImageTaskException.class, () -> resolver.resolve(456L));
        assertThrows(ImageTaskException.class, () -> resolver.resolve(0L));
    }

    @Test void tenantSchemaUsesOnlyTheAccountsStoredTenant() {
        JdbcTemplate jdbc = database();
        jdbc.execute("CREATE TABLE sys_user (user_id BIGINT PRIMARY KEY, tenant_id VARCHAR(20))");
        jdbc.update("INSERT INTO sys_user VALUES (?, ?)", 123L, "tenant-a");
        jdbc.update("INSERT INTO sys_user VALUES (?, ?)", 456L, "tenant-b");
        ImageInspirationTenantResolver resolver = new ImageInspirationTenantResolver(jdbc);
        assertEquals("tenant-a", resolver.resolve(123L));
        assertEquals("tenant-b", resolver.resolve(456L));
    }

    @Test void missingOrBlankTenantNeverFallsBackToAnotherTenant() {
        JdbcTemplate jdbc = database();
        jdbc.execute("CREATE TABLE sys_user (user_id BIGINT PRIMARY KEY, tenant_id VARCHAR(20))");
        jdbc.update("INSERT INTO sys_user VALUES (?, ?)", 123L, null);
        jdbc.update("INSERT INTO sys_user VALUES (?, ?)", 456L, "  ");
        ImageInspirationTenantResolver resolver = new ImageInspirationTenantResolver(jdbc);
        assertThrows(ImageTaskException.class, () -> resolver.resolve(123L));
        assertThrows(ImageTaskException.class, () -> resolver.resolve(456L));
        assertThrows(ImageTaskException.class, () -> resolver.resolve(789L));
    }

    @Test void unknownOrUnreadableSchemaNeverFallsBack() {
        JdbcTemplate jdbc = database();
        ImageInspirationTenantResolver resolver = new ImageInspirationTenantResolver(jdbc);
        assertThrows(DataAccessException.class, () -> resolver.resolve(123L));
        jdbc.execute("CREATE TABLE sys_user (account_id BIGINT PRIMARY KEY)");
        assertThrows(ImageTaskException.class, () -> resolver.resolve(123L));
    }

    @Test void currentSchemaWinsOverAnotherSchemasSameNamedTenantTable() throws Exception {
        JdbcTemplate jdbc = database();
        jdbc.execute("CREATE TABLE sys_user (user_id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO sys_user VALUES (?)", 123L);
        jdbc.execute("CREATE SCHEMA other");
        jdbc.execute("CREATE TABLE other.sys_user (user_id BIGINT PRIMARY KEY, tenant_id VARCHAR(20))");
        jdbc.update("INSERT INTO other.sys_user VALUES (?, ?)", 123L, "not-current");
        ImageInspirationTenantResolver resolver = new ImageInspirationTenantResolver(jdbc);
        assertEquals("000000", resolver.resolve(123L));
        SingleConnectionDataSource scoped = new SingleConnectionDataSource(jdbc.getDataSource().getConnection(), true);
        try {
            JdbcTemplate other = new JdbcTemplate(scoped);
            other.execute("SET SCHEMA other");
            assertEquals("not-current", new ImageInspirationTenantResolver(other).resolve(123L));
        } finally { scoped.destroy(); }
    }
}
