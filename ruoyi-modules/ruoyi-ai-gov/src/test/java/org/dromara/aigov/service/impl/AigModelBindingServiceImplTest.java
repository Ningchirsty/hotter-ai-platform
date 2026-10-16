package org.dromara.aigov.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.domain.AigCapabilityModel;
import org.dromara.aigov.domain.bo.AigCapabilityModelBo;
import org.dromara.aigov.helper.AigHighRiskGuard;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.mapper.AigCapabilityModelMapper;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 候选（能力×模型绑定）编辑测试（P0 补的 {@code PUT /aigov/binding/{id}}）。
 *
 * <p>钉住三件事：①不能换模型（只改用途/优先级/状态）②用途与优先级的取值校验
 * ③并发改动用"影响 0 行"报冲突而不是静默覆盖。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigModelBindingServiceImplTest {

    private AigCapabilityModelMapper capabilityModelMapper;
    private AigModelBindingServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigCapabilityModel.class);
    }

    @BeforeEach
    void setUp() {
        capabilityModelMapper = mock(AigCapabilityModelMapper.class);
        // Q2C：候选变更的高风险闸门看能力的声明等级；本类测的是候选编辑本身，
        // 因此能力查不到（=不声明等级）⇒ 闸门不参与判定，与引入 Q2C 之前逐字一致
        service = new AigModelBindingServiceImpl(capabilityModelMapper, mock(AigModelViewMapper.class),
            mock(AigCapabilityMapper.class), mock(AigHighRiskGuard.class));
    }

    private static AigCapabilityModel existing() {
        AigCapabilityModel row = new AigCapabilityModel();
        row.setBindId(5001L);
        row.setCapabilityCode("image_generation");
        row.setModelId(77L);
        row.setUsageType("PRIMARY");
        row.setPriority(10);
        row.setStatus("0");
        return row;
    }

    private static AigCapabilityModelBo bo(String usage, Integer priority) {
        AigCapabilityModelBo bo = new AigCapabilityModelBo();
        bo.setUsageType(usage);
        bo.setPriority(priority);
        return bo;
    }

    @Test
    @DisplayName("★ 只改用途/优先级/状态：能力编码与模型ID绝不进入更新体（换模型必须新增候选）")
    void updateOnlyTouchesEditableFields() {
        when(capabilityModelMapper.selectById(5001L)).thenReturn(existing());
        when(capabilityModelMapper.updateById(any(AigCapabilityModel.class))).thenReturn(1);

        service.update(5001L, bo(" fallback ", 20));

        ArgumentCaptor<AigCapabilityModel> captor = ArgumentCaptor.forClass(AigCapabilityModel.class);
        verify(capabilityModelMapper).updateById(captor.capture());
        AigCapabilityModel update = captor.getValue();
        assertEquals(5001L, update.getBindId());
        assertEquals("FALLBACK", update.getUsageType(),
            "用途要归一化成规范大写：库里存小写会让路由排序把这条候选掉到最低权重");
        assertEquals(20, update.getPriority());
        assertNull(update.getCapabilityCode(), "能力编码不可改");
        assertNull(update.getModelId(), "模型ID不可改");
    }

    @Test
    @DisplayName("没传的字段保持原值（不会把优先级清成 null）")
    void absentFieldsKeepExistingValues() {
        when(capabilityModelMapper.selectById(5001L)).thenReturn(existing());
        when(capabilityModelMapper.updateById(any(AigCapabilityModel.class))).thenReturn(1);

        service.update(5001L, bo("GRAY", null));

        ArgumentCaptor<AigCapabilityModel> captor = ArgumentCaptor.forClass(AigCapabilityModel.class);
        verify(capabilityModelMapper).updateById(captor.capture());
        assertEquals(10, captor.getValue().getPriority(), "优先级没传就保持原值");
        assertNull(captor.getValue().getStatus(), "状态没传就不改（null 由 MP 忽略该列）");
    }

    @Test
    @DisplayName("非法用途 / 越界优先级：明确拒绝（写进引擎认不出的值会让候选静默失效）")
    void rejectsInvalidUsageAndPriority() {
        when(capabilityModelMapper.selectById(5001L)).thenReturn(existing());

        ServiceException usageError = assertThrows(ServiceException.class,
            () -> service.update(5001L, bo("PRIMARY2", 10)));
        assertTrue(usageError.getMessage().contains("非法的用途"), usageError.getMessage());

        ServiceException priorityError = assertThrows(ServiceException.class,
            () -> service.update(5001L, bo("PRIMARY", 0)));
        assertTrue(priorityError.getMessage().contains("1~999"), priorityError.getMessage());

        verify(capabilityModelMapper, never()).updateById(any(AigCapabilityModel.class));
    }

    @Test
    @DisplayName("绑定不存在 / 并发改动用影响 0 行：都明确报错，不静默")
    void rejectsUnknownBindingAndConcurrentChange() {
        when(capabilityModelMapper.selectById(5001L)).thenReturn(null);
        assertTrue(assertThrows(ServiceException.class, () -> service.update(5001L, bo("GRAY", 5)))
            .getMessage().contains("不存在"));

        when(capabilityModelMapper.selectById(5001L)).thenReturn(existing());
        when(capabilityModelMapper.updateById(any(AigCapabilityModel.class))).thenReturn(0);
        assertTrue(assertThrows(ServiceException.class, () -> service.update(5001L, bo("GRAY", 5)))
            .getMessage().contains("已被其它操作改变"));

        assertTrue(assertThrows(ServiceException.class, () -> service.update(null, bo("GRAY", 5)))
            .getMessage().contains("不能为空"));
    }

}
