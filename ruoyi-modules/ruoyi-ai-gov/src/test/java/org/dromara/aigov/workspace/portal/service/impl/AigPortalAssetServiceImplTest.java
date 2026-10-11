package org.dromara.aigov.workspace.portal.service.impl;

import org.dromara.aigov.workspace.portal.domain.vo.AigPortalAssetGroupVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalMyAssetVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.asset.api.MyAssetPort;
import org.dromara.asset.api.domain.MyAssetDTO;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 门户「我的资产」聚合测试（增量 8）。
 *
 * <p>聚合层的职责只有"分组 + 透传"：可见性由各域端口负责。所以这里钉的是**聚合层自己的语义**——
 * 分组顺序稳定、没接的域不出现、接了但没数据的域要出现（两者含义不同）、
 * 每域上限被传下去、同域重复提供方只认一个、映射不漏字段。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigPortalAssetServiceImplTest {

    private static final AigPortalActor ACTOR =
        new AigPortalActor(9L, 102L, Set.of(102L, 100L), Set.of());

    @Test
    @DisplayName("按固定顺序分组：IMAGE → VIDEO → CONTENT；没接的域不出现")
    void groupsAreOrderedAndOnlyRegisteredDomainsAppear() {
        List<AigPortalAssetGroupVo> groups = groups(List.of(
            port("CONTENT", List.of(asset("CONTENT", 3L))),
            port("IMAGE", List.of(asset("IMAGE", 1L))),
            port("VIDEO", List.of())));

        assertEquals(List.of("IMAGE", "VIDEO", "CONTENT"), domains(groups));
        // VIDEO 有提供方但没数据 → 出现且为空（"没接域"与"域里没东西"必须分得开）
        assertEquals(0, groups.get(1).getItems().size());
        assertEquals("IMAGE", groups.get(0).getDomain());
        assertEquals("CONTENT", groups.get(2).getDomain());
        // 返回顺序由固定枚举决定，不随传入顺序漂移
        assertEquals(1, groups.get(0).getItems().size());
        assertEquals(1, groups.get(2).getItems().size());
    }

    @Test
    @DisplayName("每域上限被传给提供方（聚合层只展示最近几条）")
    void perDomainLimitIsPassedDown() {
        AtomicInteger seen = new AtomicInteger(-1);
        MyAssetPort port = new MyAssetPort() {
            @Override
            public String domain() {
                return "IMAGE";
            }

            @Override
            public List<MyAssetDTO> listMyAssets(long userId, int offset, int limit) {
                seen.set(limit);
                return List.of();
            }
        };

        groups(List.of(port));

        assertEquals(10, seen.get(), "每域默认取 10 条");
    }

    @Test
    @DisplayName("同一域出现多个提供方：只认第一个（结果是确定的，不被 Bean 顺序左右）")
    void duplicateDomainKeepsFirst() {
        List<AigPortalAssetGroupVo> groups = groups(List.of(
            port("IMAGE", List.of(asset("IMAGE", 1L))),
            port("image", List.of(asset("IMAGE", 2L)))));

        assertEquals(1, groups.size());
        assertEquals(1, groups.get(0).getItems().size());
        assertEquals(1L, groups.get(0).getItems().get(0).getAssetId());
    }

    @Test
    @DisplayName("没有提供方（各域模块都没上线）→ 空列表，而不是报错")
    void noPortsYieldsEmpty() {
        assertEquals(List.of(), groups(null));
        assertEquals(List.of(), groups(List.of()));
    }

    @Test
    @DisplayName("没登录直接拒绝（范围必须是当前用户）")
    void missingActorIsRejected() {
        AigPortalAssetServiceImpl service = new AigPortalAssetServiceImpl(List.of());
        assertThrows(ServiceException.class, () -> service.myAssets(null));
        assertThrows(ServiceException.class,
            () -> service.myAssets(new AigPortalActor(null, null, Set.of(), Set.of())));
    }

    @Test
    @DisplayName("映射不漏字段：类型/来源/名称/MIME/大小/任务/时间原样过去")
    void mappingKeepsDisplayFields() {
        MyAssetDTO dto = asset("IMAGE", 7L);
        dto.setAssetType("IMAGE");
        dto.setSourceKind("OUTPUT");
        dto.setName("主图.png");
        dto.setMimeType("image/png");
        dto.setSizeBytes(2048L);
        dto.setTaskId(123L);
        LocalDateTime created = LocalDateTime.of(2026, 10, 11, 8, 30);
        dto.setCreateTime(created);

        List<AigPortalAssetGroupVo> groups = groups(List.of(port("IMAGE", List.of(dto))));

        var item = groups.get(0).getItems().get(0);
        assertEquals(7L, item.getAssetId());
        assertEquals("IMAGE", item.getAssetType());
        assertEquals("OUTPUT", item.getSourceKind());
        assertEquals("主图.png", item.getName());
        assertEquals("image/png", item.getMimeType());
        assertEquals(2048L, item.getSizeBytes());
        assertEquals(123L, item.getTaskId());
        assertEquals(created, item.getCreateTime());
    }

    @Test
    @DisplayName("最近资产：按时间倒序合并，无时间的排最后，且逐条带域")
    void recentAssetsMergedByTimeDesc() {
        MyAssetDTO older = asset("IMAGE", 1L);
        older.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        MyAssetDTO newer = asset("VIDEO", 2L);
        newer.setCreateTime(LocalDateTime.of(2026, 5, 1, 0, 0));
        MyAssetDTO noTime = asset("CONTENT", 3L);

        List<AigPortalMyAssetVo> recent = recent(List.of(
            port("IMAGE", List.of(older)), port("VIDEO", List.of(newer)), port("CONTENT", List.of(noTime))));

        assertEquals(List.of(2L, 1L, 3L), recent.stream().map(AigPortalMyAssetVo::getAssetId).toList());
        // 合并视图逐条带域：没有它就看不出这条来自哪个域
        assertEquals("VIDEO", recent.get(0).getDomain());
        assertEquals("CONTENT", recent.get(2).getDomain());
    }

    @Test
    @DisplayName("最近资产有总上限：不承诺全量，也不假装是分页")
    void recentAssetsIsCapped() {
        List<MyAssetDTO> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            MyAssetDTO dto = asset("IMAGE", i);
            dto.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(i));
            many.add(dto);
        }

        List<AigPortalMyAssetVo> recent = recent(List.of(port("IMAGE", many)));

        assertEquals(20, recent.size(), "合并视图条数有上限，它不是分页");
        assertEquals(29L, recent.get(0).getAssetId(), "留下的应是最近的那些");
    }

    @Test
    @DisplayName("最近资产也把每域上限传给提供方")
    void recentPassesPerDomainLimit() {
        AtomicInteger seen = new AtomicInteger(-1);
        MyAssetPort port = new MyAssetPort() {
            @Override
            public String domain() {
                return "IMAGE";
            }

            @Override
            public List<MyAssetDTO> listMyAssets(long userId, int offset, int limit) {
                seen.set(limit);
                return List.of();
            }
        };

        recent(List.of(port));

        assertEquals(10, seen.get());
    }

    private static List<AigPortalMyAssetVo> recent(List<MyAssetPort> ports) {
        return new AigPortalAssetServiceImpl(ports).recentAssets(ACTOR);
    }

    private static List<AigPortalAssetGroupVo> groups(List<MyAssetPort> ports) {
        return new AigPortalAssetServiceImpl(ports).myAssets(ACTOR);
    }

    private static List<String> domains(List<AigPortalAssetGroupVo> groups) {
        List<String> result = new ArrayList<>();
        for (AigPortalAssetGroupVo group : groups) {
            result.add(group.getDomain());
        }
        return result;
    }

    private static MyAssetPort port(String domain, List<MyAssetDTO> assets) {
        return new MyAssetPort() {
            @Override
            public String domain() {
                return domain;
            }

            @Override
            public List<MyAssetDTO> listMyAssets(long userId, int offset, int limit) {
                return assets;
            }
        };
    }

    private static MyAssetDTO asset(String domain, long id) {
        MyAssetDTO dto = new MyAssetDTO();
        dto.setDomain(domain);
        dto.setAssetId(id);
        return dto;
    }

}
