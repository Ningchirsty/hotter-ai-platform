package org.dromara.creative.api;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.domain.CpTask;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.creative.domain.bo.CreativeProjectBo;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.scenario.api.domain.AigScenarioFlowRequest;
import org.dromara.scenario.api.domain.AigScenarioFlowResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 创作域场景端口测试（增量 18）。
 *
 * <p>钉住"场景 → 创作"这段契约的边界：①快照读不懂 → <b>业务拒绝</b>（绝不建空项目）；
 * ②合法快照走创作域自己的 {@code createProject} 并把平台任务ID带上（追溯 + 幂等）；
 * ③<b>幂等</b>——同一平台任务已建过项目就复用；④创作域自己的业务拒绝原样回；
 * ⑤受理即声明<b>交接完成</b>（项目建好就收尾平台任务，不走后续回执）。</p>
 *
 * @author creative
 */
class CreativeScenarioFlowPortTest {

    private ICreativeProjectService projectService;
    private CpTaskMapper taskMapper;
    private CreativeScenarioFlowPort port;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, CpTask.class);
    }

    @BeforeEach
    void setUp() {
        projectService = mock(ICreativeProjectService.class);
        taskMapper = mock(CpTaskMapper.class);
        port = new CreativeScenarioFlowPort(projectService, taskMapper);
    }

    @Test
    @DisplayName("适配器编码固定为 CREATIVE_EXISTING_FLOW")
    void adapterCodeIsStable() {
        assertEquals("CREATIVE_EXISTING_FLOW", port.adapter());
    }

    @Test
    @DisplayName("★合法快照：按映射建创意项目、带平台任务ID，并声明交接完成")
    void validSnapshotCreatesProjectWithHandoffComplete() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(projectService.createProject(any())).thenReturn(701L);

        AigScenarioFlowResult result = port.dispatch(request(
            "{\"taskName\":\"积木花详情页\",\"deliverableType\":\"ECOM_DETAIL\",\"productId\":123,"
                + "\"skuCode\":\"SKU-9\",\"ownerId\":77,\"deadline\":\"2026-10-20T18:00:00\"}"));

        assertTrue(result.isAccepted());
        assertTrue(result.isHandoffComplete(), "创作域受理即完成交接，平台任务可立即收尾");
        assertEquals("701", result.getExternalRef());

        ArgumentCaptor<CreativeProjectBo> captor = ArgumentCaptor.forClass(CreativeProjectBo.class);
        verify(projectService).createProject(captor.capture());
        CreativeProjectBo bo = captor.getValue();
        assertEquals("积木花详情页", bo.getTaskName());
        assertEquals("ECOM_DETAIL", bo.getDeliverableType());
        assertEquals(123L, bo.getProductId());
        assertEquals("SKU-9", bo.getSkuCode());
        assertEquals(77L, bo.getOwnerId());
        assertEquals(LocalDateTime.of(2026, 10, 20, 18, 0), bo.getDeadline());
        assertEquals(88L, bo.getPlatformTaskId(), "必须把平台任务ID带上（追溯 + 幂等）");
        assertTrue(bo.getRemark().contains("platformTaskId=88"), bo.getRemark());
    }

    @Test
    @DisplayName("缺 taskName/ownerId 时用可读兜底、owner 取提交人；deliverableType 缺省 ECOM_DETAIL")
    void missingOptionalFieldsFallBack() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(projectService.createProject(any())).thenReturn(701L);

        port.dispatch(request("{}"));

        ArgumentCaptor<CreativeProjectBo> captor = ArgumentCaptor.forClass(CreativeProjectBo.class);
        verify(projectService).createProject(captor.capture());
        CreativeProjectBo bo = captor.getValue();
        assertEquals("ECOM_DETAIL", bo.getDeliverableType());
        assertEquals(9L, bo.getOwnerId(), "缺 ownerId 时归属取提交人");
        assertTrue(bo.getTaskName().contains("88"), bo.getTaskName());
    }

    @Test
    @DisplayName("快照读不懂 / 非对象 / 缺平台任务ID：业务拒绝，绝不建项目")
    void unreadableSnapshotIsRejected() {
        when(taskMapper.selectOne(any())).thenReturn(null);

        assertFalse(port.dispatch(request("这不是 JSON")).isAccepted());
        assertFalse(port.dispatch(request("[1,2,3]")).isAccepted());
        assertFalse(port.dispatch(request(null)).isAccepted());

        AigScenarioFlowRequest noTask = request("{}");
        noTask.setTaskId(null);
        assertFalse(port.dispatch(noTask).isAccepted());

        verify(projectService, never()).createProject(any());
    }

    @Test
    @DisplayName("★幂等：同一平台任务已建过项目 → 复用同一条，不再建")
    void dispatchIsIdempotentByPlatformTaskId() {
        CpTask existing = new CpTask();
        existing.setTaskId(701L);
        existing.setPlatformTaskId(88L);
        when(taskMapper.selectOne(any())).thenReturn(existing);

        AigScenarioFlowResult result = port.dispatch(request("{\"taskName\":\"x\"}"));

        assertTrue(result.isAccepted());
        assertTrue(result.isHandoffComplete());
        assertEquals("701", result.getExternalRef());
        verify(projectService, never()).createProject(any());
    }

    @Test
    @DisplayName("创作域自己的业务拒绝（交付类型未登记/无场景档案等）原样回，不当系统故障")
    void domainRejectionIsPassedThrough() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(projectService.createProject(any()))
            .thenThrow(new ServiceException("交付类型不可用（未登记或已停用）：ECOM_DETAIL"));

        AigScenarioFlowResult result = port.dispatch(request("{\"taskName\":\"x\"}"));

        assertFalse(result.isAccepted());
        assertEquals("交付类型不可用（未登记或已停用）：ECOM_DETAIL", result.getMessage());
        assertFalse(result.isHandoffComplete());
    }

    private static AigScenarioFlowRequest request(String snapshotJson) {
        AigScenarioFlowRequest request = new AigScenarioFlowRequest();
        request.setTaskId(88L);
        request.setTaskNo("AIG-88");
        request.setScenarioCode("COMMERCE");
        request.setScenarioVersion("1.0.0");
        request.setAdapter("CREATIVE_EXISTING_FLOW");
        request.setDataLevel("INTERNAL");
        request.setRequesterId(9L);
        request.setSnapshotJson(snapshotJson);
        return request;
    }

}
