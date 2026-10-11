package org.dromara.aigov.workspace.portal.helper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.workspace.portal.domain.AigUserBrand;
import org.dromara.aigov.workspace.portal.mapper.AigUserBrandMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据库版品牌归属解析测试（④）。
 *
 * <p>钉三件事：①正常时去重并只认启用中的行（停用/删除由查询条件与 {@code @TableLogic} 排除）；
 * ②非法 userId 直接返回空、**不查库**；③查询失败**按"没有品牌"处理**（fail-closed，
 * 不让门户 500），且不静默——WARN 留痕由实现负责，这里钉行为。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigUserBrandDbResolverTest {

    private AigUserBrandMapper mapper;
    private AigUserBrandDbResolver resolver;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigUserBrand.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(AigUserBrandMapper.class);
        resolver = new AigUserBrandDbResolver(mapper);
    }

    @Test
    @DisplayName("正常：返回该用户的品牌ID（去重、跳过空行/空品牌）")
    void returnsDistinctBrandIds() {
        List<AigUserBrand> rows = new ArrayList<>();
        rows.add(row(7L));
        rows.add(row(7L));
        rows.add(row(8L));
        rows.add(row(null));
        rows.add(null);
        when(mapper.selectList(any())).thenReturn(rows);

        Set<Long> brands = resolver.brandsOf(9L);

        assertEquals(Set.of(7L, 8L), brands);
        assertTrue(resolver.available(), "数据库实现自报已接入");
    }

    @Test
    @DisplayName("非法 userId：直接空集，不查库")
    void invalidUserIdDoesNotQuery() {
        assertTrue(resolver.brandsOf(null).isEmpty());
        assertTrue(resolver.brandsOf(0L).isEmpty());
        assertTrue(resolver.brandsOf(-1L).isEmpty());
        verify(mapper, never()).selectList(any());
    }

    @Test
    @DisplayName("查库失败：按「没有品牌」处理（fail-closed），不让门户 500")
    void queryFailureIsFailClosed() {
        when(mapper.selectList(any())).thenThrow(new RuntimeException("db down"));

        assertTrue(resolver.brandsOf(9L).isEmpty(),
            "读不到归属时应当少给可见性，而不是让门户打不开");
    }

    private static AigUserBrand row(Long brandId) {
        AigUserBrand entity = new AigUserBrand();
        entity.setBrandId(brandId);
        return entity;
    }

}
