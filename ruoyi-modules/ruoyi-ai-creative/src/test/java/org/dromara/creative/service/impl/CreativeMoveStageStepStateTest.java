package org.dromara.creative.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.helper.ContentOssHelper;
import org.dromara.content.service.IContentProductService;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.domain.DpStageEvent;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.helper.CreativeStepStateWriter;
import org.dromara.creative.mapper.CreativeTaskStageMapper;
import org.dromara.creative.mapper.DpStageEventMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code moveStage} 的「一次变更、两处留痕」纪律的单元测试（V0.2 D2）。
 *
 * <p>D2 的三条纪律里，两条落在这个方法上，而且都只能在这里钉：</p>
 * <ol>
 *   <li><b>单一写入点</b>：{@code cp_task.visual_stage} 与 {@code dp_project_step_state}
 *       都在 moveStage 里写，且步骤状态只经 {@link CreativeStepStateWriter} 写；</li>
 *   <li><b>同时写事件</b>：一次阶段变更必须伴一条 {@code dp_stage_event}。</li>
 * </ol>
 *
 * <p>另外钉住降级策略：<b>派生表写失败不让阶段变更失败</b>（阶段与事件才是权威，
 * 步骤状态可重建），但也不能因此把事件一起丢掉。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeMoveStageStepStateTest {

    @Mock
    private IContentTaskService contentTaskService;

    @Mock
    private IContentProductService productService;

    @Mock
    private ContentOssHelper contentOssHelper;

    @Mock
    private CreativeTaskStageMapper stageMapper;

    @Mock
    private CreativeStepStateWriter stepStateWriter;

    @Mock
    private DpStageEventMapper eventMapper;

    @InjectMocks
    private CreativeProjectServiceImpl service;

    private static final Long TASK_ID = 2100000000000000999L;

    /**
     * 让项目处于指定阶段。
     *
     * @param stage 当前阶段编码
     */
    private void givenStage(String stage) {
        Map<String, Object> row = new HashMap<>();
        row.put("taskId", TASK_ID);
        row.put("visualStage", stage);
        when(stageMapper.selectStage(TASK_ID)).thenReturn(row);
        when(stageMapper.selectDeliverableType(TASK_ID)).thenReturn("ECOM_DETAIL");
    }

    @Test
    @DisplayName("阶段前进：更新阶段列 + 写事件 + 同步步骤状态，三件事同一次调用完成")
    void moveStageWritesStageEventAndStepState() {
        givenStage("DNA_LOCKED");

        service.moveStage(TASK_ID, DpVisualStageEnum.DIRECTION_GENERATING, "START_DIRECTION", "{}");

        verify(stageMapper, times(1)).updateStage(TASK_ID, "DIRECTION_GENERATING");

        ArgumentCaptor<DpStageEvent> event = ArgumentCaptor.forClass(DpStageEvent.class);
        verify(eventMapper, times(1)).insert(event.capture());
        assertEquals("STAGE", event.getValue().getEventType());
        assertEquals("DNA_LOCKED", event.getValue().getFromStage());
        assertEquals("DIRECTION_GENERATING", event.getValue().getToStage());
        assertEquals("START_DIRECTION", event.getValue().getAction());

        // 步骤状态同步：拿的是"变更后"的阶段与项目的交付类型
        verify(stepStateWriter, times(1))
            .sync(eq(TASK_ID), eq("DIRECTION_GENERATING"), eq("ECOM_DETAIL"));
    }

    @Test
    @DisplayName("原地重算（目标=当前阶段）：不更新阶段列，但事件与步骤状态照写")
    void sameStageStillWritesEventAndSyncsState() {
        givenStage("PRODUCING");

        service.moveStage(TASK_ID, DpVisualStageEnum.PRODUCING, "REGENERATE", null);

        verify(stageMapper, never()).updateStage(any(Long.class), anyString());
        verify(eventMapper, times(1)).insert(any(DpStageEvent.class));
        verify(stepStateWriter, times(1)).sync(TASK_ID, "PRODUCING", "ECOM_DETAIL");
    }

    @Test
    @DisplayName("非法回退：直接拒绝，事件与步骤状态都不写（阶段机仍是唯一判据）")
    void illegalMoveIsRejectedBeforeAnyWrite() {
        givenStage("COMPLETED");

        ServiceException ex = assertThrows(ServiceException.class, () -> service
            .moveStage(TASK_ID, DpVisualStageEnum.DNA_REVIEW, "BACK", null));

        assertTrue(ex.getMessage().contains("不能再变为"), ex.getMessage());
        verify(stageMapper, never()).updateStage(any(Long.class), anyString());
        verify(eventMapper, never()).insert(any(DpStageEvent.class));
        verify(stepStateWriter, never()).sync(any(Long.class), anyString(), anyString());
    }

    @Test
    @DisplayName("步骤状态写失败不让阶段变更失败：阶段已更新、事件已落，异常不外抛")
    void stepStateFailureDoesNotRollBackStageChange() {
        givenStage("MATERIAL_READY");
        when(stepStateWriter.sync(any(Long.class), anyString(), anyString()))
            .thenThrow(new RuntimeException("dp_project_step_state 表还没建"));

        service.moveStage(TASK_ID, DpVisualStageEnum.PRODUCING, "START_GENERATION", null);

        verify(stageMapper, times(1)).updateStage(TASK_ID, "PRODUCING");
        verify(eventMapper, times(1)).insert(any(DpStageEvent.class));
    }

    @Test
    @DisplayName("交付后返工（COMPLETED → PRODUCING）也照常同步步骤状态")
    void postDeliveryReworkSyncsState() {
        givenStage("COMPLETED");

        service.moveStage(TASK_ID, DpVisualStageEnum.PRODUCING, "REDO_GENERATION", null);

        verify(stageMapper, times(1)).updateStage(TASK_ID, "PRODUCING");
        verify(stepStateWriter, times(1)).sync(TASK_ID, "PRODUCING", "ECOM_DETAIL");
    }
}
