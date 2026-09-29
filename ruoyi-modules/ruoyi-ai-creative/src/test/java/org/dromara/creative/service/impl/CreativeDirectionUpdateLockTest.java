package org.dromara.creative.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.creative.domain.DpVisualDirection;
import org.dromara.creative.domain.bo.CreativeDirectionBo;
import org.dromara.creative.helper.CreativeDraftBrain;
import org.dromara.creative.mapper.DpVisualDirectionMapper;
import org.dromara.creative.service.ICreativeDnaService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.content.service.IContentTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「已选定的视觉方向不能原地修改」的单元测试（V0.2 FIX-004）。
 *
 * <p>为什么必须钉住：方向一旦选定，后面 DNA 派生提示词、分镜、排版都按它产出；
 * 允许原地改文案会让"已认定的方向"与"已经按它做出来的东西"对不上，
 * 而且没有任何留痕能看出改过。规则要与分镜锁定一致：要改就重新生成新版本再重新选定。</p>
 *
 * <p>用确定性单测而不是真跑一遍：这条规则靠"状态"分支，真实链路里要造出"已选定"状态
 * 需要跑完生成+选定（动数据、动阶段），成本高且不可控。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeDirectionUpdateLockTest {

    private static final long TASK_ID = 2102948730396520450L;
    private static final long DIRECTION_ID = 2102949400000000123L;

    @Mock
    private DpVisualDirectionMapper directionMapper;
    @Mock
    private ICreativeDnaService dnaService;
    @Mock
    private ICreativeProjectService projectService;
    @Mock
    private IContentTaskService contentTaskService;
    @Mock
    private CreativeDraftBrain brain;

    private CreativeDirectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CreativeDirectionServiceImpl(directionMapper, dnaService, projectService,
            contentTaskService, brain);
    }

    private DpVisualDirection direction(String status) {
        DpVisualDirection entity = new DpVisualDirection();
        entity.setId(DIRECTION_ID);
        entity.setTaskId(TASK_ID);
        entity.setDirectionCode("A");
        entity.setDirectionName("A · 极简留白");
        entity.setConcept("原概念");
        entity.setStatus(status);
        return entity;
    }

    private CreativeDirectionBo form() {
        CreativeDirectionBo bo = new CreativeDirectionBo();
        bo.setId(DIRECTION_ID);
        bo.setDirectionName("改过的名字");
        bo.setConcept("改过的概念");
        return bo;
    }

    @Test
    @DisplayName("已选定（SELECTED）的方向：拒绝原地修改，并给出「重新生成」的出路")
    void selectedDirectionCannotBeEditedInPlace() {
        when(directionMapper.selectById(DIRECTION_ID)).thenReturn(direction("SELECTED"));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.update(TASK_ID, form()));
        assertTrue(ex.getMessage().contains("已选定"), "报错要说清是「已选定」这个原因：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("重新生成"), "报错要给出出路：" + ex.getMessage());
        // 关键：拒绝就不能落库（不能"先改了再报错"）
        verify(directionMapper, never()).updateById(any(DpVisualDirection.class));
    }

    @Test
    @DisplayName("待选定（GENERATED）的方向：允许改文案，并且真的落库")
    void generatedDirectionCanStillBeEdited() {
        DpVisualDirection entity = direction("GENERATED");
        when(directionMapper.selectById(DIRECTION_ID)).thenReturn(entity);

        service.update(TASK_ID, form());

        assertEquals("改过的名字", entity.getDirectionName());
        assertEquals("改过的概念", entity.getConcept());
        verify(directionMapper).updateById(entity);
    }

    @Test
    @DisplayName("已弃用（REJECTED）的方向：允许改（它不再被引用），不影响选定基准")
    void rejectedDirectionCanBeEdited() {
        DpVisualDirection entity = direction("REJECTED");
        when(directionMapper.selectById(DIRECTION_ID)).thenReturn(entity);

        service.update(TASK_ID, form());

        verify(directionMapper).updateById(entity);
    }

    @Test
    @DisplayName("不属于该项目的方向：先被归属校验拦住（守卫顺序不能反）")
    void directionOfAnotherTaskIsRejectedByOwnershipCheck() {
        DpVisualDirection entity = direction("SELECTED");
        entity.setTaskId(TASK_ID + 1);
        when(directionMapper.selectById(DIRECTION_ID)).thenReturn(entity);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.update(TASK_ID, form()));
        assertTrue(ex.getMessage().contains("不属于该项目"), ex.getMessage());
        verify(directionMapper, never()).updateById(any(DpVisualDirection.class));
    }
}
