package org.dromara.aigov.helper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.aigov.domain.bo.AigCapabilityBo;
import org.dromara.aigov.domain.bo.AigRoutePolicyBo;
import org.dromara.aigov.domain.vo.AigRoutePolicyVo;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.mapper.AigRoutePolicyMapper;
import org.dromara.aigov.service.impl.AigCapabilityServiceImpl;
import org.dromara.aigov.service.impl.AigRoutePolicyServiceImpl;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Q1C 高风险变更门槛：规则本身 + 三个入口的接线。
 *
 * <p><b>为什么规则与接线都要测</b>：规则错了会拦住正常变更（人就会想办法绕过去），
 * 接线漏了则等于没防——而这两种错都不会让别的测试变红。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigHighRiskChangesTest {

    private AigRoutePolicyMapper routePolicyMapper;
    private AigCapabilityMapper capabilityMapper;
    private AigHighRiskGuard guard;
    private AigRoutePolicyServiceImpl policyService;
    private AigCapabilityServiceImpl capabilityService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigRoutePolicy.class);
        TableInfoHelper.initTableInfo(assistant, AigCapability.class);
    }

    @BeforeEach
    void setUp() {
        routePolicyMapper = mock(AigRoutePolicyMapper.class);
        capabilityMapper = mock(AigCapabilityMapper.class);
        guard = mock(AigHighRiskGuard.class);
        policyService = new AigRoutePolicyServiceImpl(routePolicyMapper, capabilityMapper, guard);
        capabilityService = new AigCapabilityServiceImpl(capabilityMapper, guard);
        // existsPolicy / existsCode 会读 selectCount；mock 默认返回 null 会让 `> 0` NPE
        when(routePolicyMapper.selectCount(any())).thenReturn(0L);
        when(capabilityMapper.selectCount(any())).thenReturn(0L);
    }

    /**
     * 造一行入库的策略。
     *
     * @param dataLevel   数据等级
     * @param allowExternal 是否允许外发
     * @param preferred   首选部署
     * @return 策略实体
     */
    private static AigRoutePolicy policy(String dataLevel, String allowExternal, String preferred) {
        AigRoutePolicy entity = new AigRoutePolicy();
        entity.setPolicyId(9L);
        entity.setCapabilityCode("image_generation");
        entity.setDataLevel(dataLevel);
        entity.setAllowExternal(allowExternal);
        entity.setPreferredDeployment(preferred);
        return entity;
    }

    /**
     * 造一份策略入参。
     *
     * @param dataLevel    数据等级
     * @param allowExternal 是否允许外发
     * @param preferred    首选部署
     * @return 入参
     */
    private static AigRoutePolicyBo policyBo(String dataLevel, String allowExternal, String preferred) {
        AigRoutePolicyBo bo = new AigRoutePolicyBo();
        bo.setPolicyId(9L);
        bo.setCapabilityCode("image_generation");
        bo.setDataLevel(dataLevel);
        bo.setAllowExternal(allowExternal);
        bo.setPreferredDeployment(preferred);
        return bo;
    }

    @Test
    @DisplayName("★ 放开外发 = 高风险；公开/内部级且禁止外发 = 不高风险（不能把正常变更也拦了）")
    void rulesOnAllowExternal() {
        assertTrue(AigHighRiskChanges.policyRow("INTERNAL", "Y", "LOCAL"));
        assertTrue(AigHighRiskChanges.policyRow("INTERNAL", "y", null), "大小写不能改变结论");
        assertFalse(AigHighRiskChanges.policyRow("INTERNAL", "N", "LOCAL"));
        assertFalse(AigHighRiskChanges.policyRow("PUBLIC", null, "GROUP"));
    }

    @Test
    @DisplayName("★ 限制级/严格级策略 = 高风险（哪怕禁止外发）；钉到外部部署也是")
    void rulesOnLevelAndDeployment() {
        assertTrue(AigHighRiskChanges.policyRow("RESTRICTED", "N", "LOCAL"));
        assertTrue(AigHighRiskChanges.policyRow("STRICT", "N", null));
        assertTrue(AigHighRiskChanges.policyRow("INTERNAL", "N", "EXTERNAL_API"));
        assertTrue(AigHighRiskChanges.policyRow("PUBLIC", "N", "EXTERNAL_ENTERPRISE"),
            "首选外部部署本身就是外发表态");
        // 本地/集团共享都不是外部，且等级不高、也没放开外发 → 不高风险
        assertFalse(AigHighRiskChanges.policyRow("INTERNAL", "N", "GROUP"));
    }

    @Test
    @DisplayName("编辑：新旧任一行命中就算；收紧（把外发关掉）不算")
    void rulesOnUpdate() {
        // 已放行外发的行，哪怕这次改成 N（收紧）仍按高风险处理：改之前那一行影响了现网路由结果
        assertTrue(AigHighRiskChanges.forUpdatedPolicy(policy("INTERNAL", "Y", "LOCAL"),
            policyBo("INTERNAL", "N", "LOCAL")));
        // N→Y：只看新值也能命中（放行外发）
        assertTrue(AigHighRiskChanges.forUpdatedPolicy(policy("INTERNAL", "N", "LOCAL"),
            policyBo("INTERNAL", "Y", "LOCAL")));
        // 内部级、禁止外发、本地部署 → 改备注不该被拦
        assertFalse(AigHighRiskChanges.forUpdatedPolicy(policy("INTERNAL", "N", "LOCAL"),
            policyBo(null, null, null)), "PATCH 式更新（不带这几列）应沿用现有值");
        // 等级从限制级降到内部级：改之前是限制级 ⇒ 仍高风险
        assertTrue(AigHighRiskChanges.forUpdatedPolicy(policy("RESTRICTED", "N", "LOCAL"),
            policyBo("INTERNAL", "N", "LOCAL")));
    }

    @Test
    @DisplayName("删除：删掉限制级/已放行外发的行 = 高风险（删掉限制的效果与放开外发相同）")
    void rulesOnRemove() {
        assertFalse(AigHighRiskChanges.forRemovedPolicy(policy("INTERNAL", "N", "LOCAL")));
        assertTrue(AigHighRiskChanges.forRemovedPolicy(policy("INTERNAL", "Y", "LOCAL")));
        assertTrue(AigHighRiskChanges.forRemovedPolicy(policy("STRICT", "N", "LOCAL")));
        assertFalse(AigHighRiskChanges.forRemovedPolicy(null));
    }

    @Test
    @DisplayName("停用能力：只判「启用 → 停用」这一个迁移（已停用的能力改描述不该被拦）")
    void rulesOnCapabilityDisable() {
        assertTrue(AigHighRiskChanges.forCapabilityDisable("0", "1"));
        assertFalse(AigHighRiskChanges.forCapabilityDisable("1", "1"), "本来就是停用的，再存一次不算");
        assertFalse(AigHighRiskChanges.forCapabilityDisable("1", "0"), "重新启用不是放宽边界的动作");
        assertFalse(AigHighRiskChanges.forCapabilityDisable("0", null), "不带状态 = 不改状态");
    }

    @Test
    @DisplayName("★ 限制级/严格级能力（按其声明等级）的候选变更算高风险；未声明/低等级不算")
    void rulesOnCandidateChange() {
        assertTrue(AigHighRiskChanges.forCandidateChange("RESTRICTED"));
        assertTrue(AigHighRiskChanges.forCandidateChange("strict"));
        assertFalse(AigHighRiskChanges.forCandidateChange("INTERNAL"));
        assertFalse(AigHighRiskChanges.forCandidateChange("PUBLIC"));
        assertFalse(AigHighRiskChanges.forCandidateChange(null));
        assertFalse(AigHighRiskChanges.forCandidateChange("  "));
    }

    @Test
    @DisplayName("★ 声明等级被调低或清空 = 高风险（松锁）；调高或从无到有 = 收紧，不拦")
    void rulesOnDeclarationChange() {
        // 调低：松锁
        assertTrue(AigHighRiskChanges.forMinDataLevelChange("RESTRICTED", "INTERNAL"));
        assertTrue(AigHighRiskChanges.forMinDataLevelChange("STRICT", "RESTRICTED"));
        // 清空：同样是把锁摘掉
        assertTrue(AigHighRiskChanges.forMinDataLevelChange("RESTRICTED", null));
        assertTrue(AigHighRiskChanges.forMinDataLevelChange("RESTRICTED", "  "));
        // 调高或从无到有：收紧，不需要额外权限
        assertFalse(AigHighRiskChanges.forMinDataLevelChange("INTERNAL", "RESTRICTED"));
        assertFalse(AigHighRiskChanges.forMinDataLevelChange(null, "STRICT"));
        assertFalse(AigHighRiskChanges.forMinDataLevelChange("RESTRICTED", "RESTRICTED"));
        // 之前就没有声明时谈不上"调低"
        assertFalse(AigHighRiskChanges.forMinDataLevelChange(null, null));
    }

    @Test
    @DisplayName("★ 接线：新增放行外发的策略会过闸门，闸门拒绝时一次都不写库")
    void newPolicyGoesThroughGuard() {
        doThrow(new ServiceException("没有高风险权限")).when(guard).assertAllowed(any());
        assertThrows(ServiceException.class,
            () -> policyService.create(policyBo("INTERNAL", "Y", "LOCAL")));
        verify(routePolicyMapper, never()).insert(any(AigRoutePolicy.class));
    }

    @Test
    @DisplayName("接线：内部级禁止外发的新增策略不过闸门（否则权限点会变成拦正常变更）")
    void safeNewPolicySkipsGuard() {
        when(routePolicyMapper.insert(any(AigRoutePolicy.class))).thenReturn(1);
        policyService.create(policyBo("INTERNAL", "N", "LOCAL"));
        verify(guard, never()).assertAllowed(any());
        verify(routePolicyMapper).insert(any(AigRoutePolicy.class));
    }

    @Test
    @DisplayName("★ 接线：编辑 N→Y 过闸门；删除限制行也过闸门")
    void updateAndRemoveGoThroughGuard() {
        when(routePolicyMapper.selectById(9L)).thenReturn(policy("INTERNAL", "N", "LOCAL"));
        when(routePolicyMapper.updateById(any(AigRoutePolicy.class))).thenReturn(1);
        policyService.update(policyBo("INTERNAL", "Y", "LOCAL"));
        verify(guard).assertAllowed(contains("修改"));

        when(routePolicyMapper.selectById(9L)).thenReturn(policy("STRICT", "N", "LOCAL"));
        policyService.remove(9L);
        verify(guard).assertAllowed(contains("删除"));
    }

    @Test
    @DisplayName("★ 接线：能力删除一律过闸门；停用（0→1）过闸门，改描述不过")
    void capabilityEntryPointsGoThroughGuard() {
        AigCapability existing = new AigCapability();
        existing.setCapabilityId(7L);
        existing.setCapabilityCode("image_generation");
        existing.setStatus("0");
        when(capabilityMapper.selectById(7L)).thenReturn(existing);
        when(capabilityMapper.updateById(any(AigCapability.class))).thenReturn(1);

        AigCapabilityBo disable = new AigCapabilityBo();
        disable.setCapabilityId(7L);
        disable.setCapabilityCode("image_generation");
        disable.setStatus("1");
        capabilityService.update(disable);
        verify(guard).assertAllowed(contains("停用"));

        // 改描述（不带状态）不过闸门。⚠️ 必须先清掉上一步的调用记录：
        // verify(never()) 数的是"这个 mock 到目前的所有调用"，不清会把前面那次合法调用算进来
        clearInvocations(guard);
        AigCapabilityBo rename = new AigCapabilityBo();
        rename.setCapabilityId(7L);
        rename.setCapabilityCode("image_generation");
        capabilityService.update(rename);
        verify(guard, never()).assertAllowed(any());

        capabilityService.remove(7L);
        verify(guard).assertAllowed(contains("删除"));
    }

    @Test
    @DisplayName("策略详情视图与实体同名字段（规则读的是实体，避免以后字段改名时静默读空）")
    void ruleReadsEntityColumns() {
        AigRoutePolicyVo vo = new AigRoutePolicyVo();
        vo.setAllowExternal("Y");
        vo.setDataLevel("INTERNAL");
        assertTrue(AigHighRiskChanges.policyRow(vo.getDataLevel(), vo.getAllowExternal(), null));
    }
}
