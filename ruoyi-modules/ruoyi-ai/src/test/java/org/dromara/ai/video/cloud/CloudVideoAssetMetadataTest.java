package org.dromara.ai.video.cloud;
import org.dromara.ai.video.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 实测素材字段必须从 JDBC 行映射返回，同时保留所有权条件。 */
class CloudVideoAssetMetadataTest {
 @Test void ownedAssetRetainsDimensionsAndDurationFromDatabase()throws Exception{
  var jdbc=mock(JdbcTemplate.class);var rs=mock(ResultSet.class);
  when(rs.getObject("width")).thenReturn(1280);when(rs.getObject("height")).thenReturn(720);when(rs.getObject("duration_ms")).thenReturn(5000L);when(rs.getObject("create_dept")).thenReturn(3L);
  when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<VideoTaskRepository.AssetRow>>any(),eq(11L),eq("tenant"),eq(7L))).thenAnswer(inv->{
   String sql=inv.getArgument(0);assertTrue(sql.contains("width, height, duration_ms, create_dept"));assertTrue(sql.contains("id = ? AND tenant_id = ? AND user_id = ? AND del_flag = '0'"));
   RowMapper<VideoTaskRepository.AssetRow> mapper=inv.getArgument(1);return List.of(mapper.mapRow(rs,0));
  });
  var asset=new JdbcVideoTaskRepository(jdbc).requireOwnedAsset(11,"tenant",7);
  assertEquals(1280,asset.width());assertEquals(720,asset.height());assertEquals(5000L,asset.durationMillis());assertEquals(3L,asset.createDept());
 }
}
