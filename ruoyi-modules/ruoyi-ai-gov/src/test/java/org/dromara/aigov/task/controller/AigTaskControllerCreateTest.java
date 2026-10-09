package org.dromara.aigov.task.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.task.domain.bo.AigTaskCreateBo;
import org.dromara.aigov.task.service.IAigTaskExecutor;
import org.dromara.aigov.task.service.IAigTaskScheduler;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.common.core.domain.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务控制器的「建任务」入口测试。
 *
 * <p><b>为什么补这个入口</b>：此前任务只能由业务域在进程内创建，HTTP 面只有查询/取消/重放/执行，
 * 于是"运维手工建一条平台任务跑一次"只能靠直接写库——那会绕过快照冻结、幂等键与数据等级收紧。
 * 这个接口把它拉回正规入口。</p>
 *
 * <p><b>两条必须钉住的</b>：①它必须带 {@code aig:task:operate}（建任务会真的产生一次平台执行，
 * 可能计费——忘了权限点就是一个开放的计费入口）；②它只创建、不推进状态
 * （状态推进归调度器与既有动作，见控制器 javadoc）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigTaskControllerCreateTest {

    private IAigTaskService taskService;
    private AigTaskController controller;

    @BeforeEach
    void setUp() {
        taskService = mock(IAigTaskService.class);
        controller = new AigTaskController(taskService, mock(IAigTaskScheduler.class),
            mock(IAigTaskExecutor.class));
    }

    @Test
    @DisplayName("建任务：透传给服务层并返回任务ID（不在这里做任何状态推进）")
    void createDelegatesToService() {
        when(taskService.create(any())).thenReturn(4242L);
        AigTaskCreateBo bo = new AigTaskCreateBo();
        bo.setTaskType("TEXT_GENERATION");
        bo.setProjectType("CONTENT");
        bo.setDataLevel("INTERNAL");
        bo.setSnapshotJson("{\"facts\":\"x\"}");

        R<Long> result = controller.create(bo);

        assertEquals(4242L, result.getData());
        ArgumentCaptor<AigTaskCreateBo> captor = ArgumentCaptor.forClass(AigTaskCreateBo.class);
        verify(taskService).create(captor.capture());
        assertEquals("TEXT_GENERATION", captor.getValue().getTaskType());
    }

    @Test
    @DisplayName("★ 建任务必须要求 aig:task:operate（它是会真花钱的入口，不能是开放接口）")
    void createRequiresOperatePermission() throws Exception {
        Method method = AigTaskController.class.getMethod("create", AigTaskCreateBo.class);
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);

        assertNotNull(permission, "建任务没有权限注解 = 任何人都能起一条会计费的任务");
        assertTrue(java.util.Arrays.asList(permission.value()).contains(AigConstants.PERM_TASK_OPERATE),
            "权限点应为 " + AigConstants.PERM_TASK_OPERATE + "，实际=" + java.util.Arrays.toString(permission.value()));
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertNotNull(mapping, "建任务的 HTTP 方法必须是 POST");
        assertEquals(0, mapping.value().length, "建任务应挂在 /aigov/task 根路径上（不带子路径）");
    }

}
