package org.dromara.ai.video.cloud;

import org.dromara.ai.video.exception.VideoTaskException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.SQLException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudVideoTenantTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Long user = 1761100000000000001L;

    @Test void tenantEnabledUsesAuthenticatedUsersTenant() {
        when(jdbc.queryForList(anyString(),eq(String.class),eq(user))).thenReturn(List.of("tenant-a"));
        assertThat(VideoCloudTenantResolver.resolve(jdbc,user)).isEqualTo("tenant-a");
    }
    @Test void singleTenantSchemaRequiresExistingUserBeforeFallback() {
        when(jdbc.queryForList(anyString(),eq(String.class),eq(user))).thenThrow(missingTenant());
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(user))).thenReturn(1);
        assertThat(VideoCloudTenantResolver.resolve(jdbc,user)).isEqualTo("000000");
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(user))).thenReturn(0);
        assertThatThrownBy(()->VideoCloudTenantResolver.resolve(jdbc,user)).isInstanceOf(VideoTaskException.class);
    }
    @Test void databaseFailuresCannotBecomeDefaultTenant() {
        var failure=new BadSqlGrammarException("query","SELECT",new SQLException("denied","42000",1142));
        when(jdbc.queryForList(anyString(),eq(String.class),eq(user))).thenThrow(failure);
        assertThatThrownBy(()->VideoCloudTenantResolver.resolve(jdbc,user)).isSameAs(failure);
        verify(jdbc,never()).queryForObject(anyString(),eq(Integer.class),anyLong());
    }
    @Test void unauthenticatedAndMissingTenantAreRejected() {
        assertThatThrownBy(()->VideoCloudTenantResolver.resolve(jdbc,null)).isInstanceOf(VideoTaskException.class);
        when(jdbc.queryForList(anyString(),eq(String.class),eq(user))).thenReturn(List.of());
        assertThatThrownBy(()->VideoCloudTenantResolver.resolve(jdbc,user)).isInstanceOf(VideoTaskException.class);
    }
    private BadSqlGrammarException missingTenant() {
        return new BadSqlGrammarException("query","SELECT tenant_id",new SQLException("Unknown column tenant_id","42S22",1054));
    }
}
