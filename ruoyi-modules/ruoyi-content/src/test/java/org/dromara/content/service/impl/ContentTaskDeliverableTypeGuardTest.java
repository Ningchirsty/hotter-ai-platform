package org.dromara.content.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.domain.CpTask;
import org.dromara.content.domain.bo.ContentTaskBo;
import org.dromara.content.mapper.CpTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 「已开工任务不许改交付类型」的单元测试（内测 S17 的回归钉）。
 *
 * <p><b>为什么值得钉</b>：视觉工厂的「视觉项目」列表是按交付类型过滤 {@code cp_task} 的。
 * 类型一改，这条任务立刻从设计部列表里消失、项目详情被拒，而它名下的出图候选、分镜、
 * 排版版本都还在库里——只是再也打不开。实测现象是「设计师的项目凭空没了」，
 * 而且<b>不报任何错</b>，所以必须在写入侧拦住。</p>
 *
 * <p>同时钉住两个反例，避免修成"一律禁止改类型"：设计部还没碰过的任务、以及原值回传
 * （用户只是改了任务名），都不该被拦。</p>
 *
 * @author content
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContentTaskDeliverableTypeGuardTest {

    private static final Long TASK_ID = 9101L;

    @Mock
    private CpTaskMapper taskMapper;

    @InjectMocks
    private ContentTaskServiceImpl service;

    /**
     * 造一条库里的任务。
     *
     * @param type 交付类型
     * @return 任务实体
     */
    private static CpTask stored(String type) {
        CpTask task = new CpTask();
        task.setTaskId(TASK_ID);
        task.setDeliverableType(type);
        task.setStatus("CONDITIONAL_READY");
        return task;
    }

    /**
     * 造一份提交表单。
     *
     * @param type 交付类型
     * @return 表单
     */
    private static ContentTaskBo form(String type) {
        ContentTaskBo bo = new ContentTaskBo();
        bo.setTaskId(TASK_ID);
        bo.setDeliverableType(type);
        return bo;
    }

    @Test
    @DisplayName("S17：设计部已开工（visual_stage 非空）→ 改交付类型被拒，且提示可读")
    void rejectsTypeChangeAfterVisualWorkStarted() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(stored("ECOM_DETAIL"));
        when(taskMapper.selectVisualStage(TASK_ID)).thenReturn("PRODUCING");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.update(form("MAIN_IMAGE")), "已开工还允许改类型，项目就会凭空消失");

        assertTrue(ex.getMessage().contains("视觉工厂"), "提示要说清为什么不能改：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("新建一条任务"), "提示要给出出路：" + ex.getMessage());
    }

    @Test
    @DisplayName("S17 反例：还没进视觉工厂（visual_stage 为空）→ 允许改，不该一律禁止")
    void allowsTypeChangeBeforeVisualWork() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(stored("ECOM_DETAIL"));
        when(taskMapper.selectVisualStage(TASK_ID)).thenReturn(null);

        assertDoesNotThrow(() -> service.update(form("MAIN_IMAGE")));
    }

    @Test
    @DisplayName("S17 反例：交付类型没变（原值回传）→ 不触发检查，不该被误拦")
    void allowsUnchangedType() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(stored("ECOM_DETAIL"));

        assertDoesNotThrow(() -> service.update(form("ECOM_DETAIL")));
    }
}
