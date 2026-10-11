package org.dromara.content.api;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.domain.CpTask;
import org.dromara.content.domain.bo.ContentTaskBo;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.content.service.IContentTaskService;
import org.dromara.scenario.api.domain.AigScenarioFlowRequest;
import org.dromara.scenario.api.domain.AigScenarioFlowResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内容域场景端口测试（增量 15）。
 *
 * <p>钉住的是"场景 → 内容"这段契约的边界：①快照缺 `deliverableType`/读不懂 → **业务拒绝**
 * （绝不建一条空内容任务）；②合法快照按映射建任务并回 `externalRef`；③**幂等**——
 * 同一平台任务再派发只复用同一条内容任务；④内容域自己的业务拒绝原样回，不当系统故障。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class ContentScenarioFlowPortTest {

    private IContentTaskService contentTaskService;
    private CpTaskMapper taskMapper;
    private ContentScenarioFlowPort port;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, CpTask.class);
    }

    @BeforeEach
    void setUp() {
        contentTaskService = mock(IContentTaskService.class);
        taskMapper = mock(CpTaskMapper.class);
        port = new ContentScenarioFlowPort(contentTaskService, taskMapper, JsonMapper.builder().build());
    }

    @Test
    @DisplayName("适配器编码固定为 CONTENT_EXISTING_FLOW")
    void adapterCodeIsStable() {
        assertEquals("CONTENT_EXISTING_FLOW", port.adapter());
    }

    @Test
    @DisplayName("★合法快照：按映射建内容任务并回 externalRef")
    void validSnapshotCreatesContentTask() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(contentTaskService.create(any())).thenReturn(401L);

        AigScenarioFlowResult result = port.dispatch(request(
            "{\"deliverableType\":\"ECOM_DETAIL\",\"taskName\":\"积木花详情页\",\"productId\":123,"
                + "\"skuCode\":\"SKU-9\",\"ownerId\":77}"));

        assertTrue(result.isAccepted());
        assertEquals("401", result.getExternalRef());
        ArgumentCaptor<ContentTaskBo> captor = ArgumentCaptor.forClass(ContentTaskBo.class);
        verify(contentTaskService).create(captor.capture());
        ContentTaskBo bo = captor.getValue();
        assertEquals("ECOM_DETAIL", bo.getDeliverableType());
        assertEquals("积木花详情页", bo.getTaskName());
        assertEquals(123L, bo.getProductId());
        assertEquals("SKU-9", bo.getSkuCode());
        assertEquals(77L, bo.getOwnerId());
        assertEquals(88L, bo.getPlatformTaskId(), "必须把平台任务ID带上（追溯 + 幂等）");
        assertEquals("INTERNAL", bo.getDataLevel(), "数据等级取请求的");
        assertTrue(bo.getRemark().contains("platformTaskId=88"), bo.getRemark());
    }

    @Test
    @DisplayName("快照缺 deliverableType / 非法：业务拒绝，绝不建空任务")
    void missingOrInvalidDeliverableTypeIsRejected() {
        when(taskMapper.selectOne(any())).thenReturn(null);

        AigScenarioFlowResult missing = port.dispatch(request("{\"taskName\":\"x\"}"));
        assertFalse(missing.isAccepted());
        assertTrue(missing.getMessage().contains("deliverableType"), missing.getMessage());

        AigScenarioFlowResult invalid = port.dispatch(request("{\"deliverableType\":\"NOT_A_TYPE\"}"));
        assertFalse(invalid.isAccepted());
        assertTrue(invalid.getMessage().contains("非法"), invalid.getMessage());

        AigScenarioFlowResult notJson = port.dispatch(request("这不是 JSON"));
        assertFalse(notJson.isAccepted());

        assertFalse(port.dispatch(request(null)).isAccepted());
        verify(contentTaskService, never()).create(any());
    }

    @Test
    @DisplayName("★幂等：同一平台任务已建过内容任务 → 复用同一条，不再建")
    void dispatchIsIdempotentByPlatformTaskId() {
        CpTask existing = new CpTask();
        existing.setTaskId(401L);
        existing.setPlatformTaskId(88L);
        when(taskMapper.selectOne(any())).thenReturn(existing);

        AigScenarioFlowResult result = port.dispatch(request("{\"deliverableType\":\"ECOM_DETAIL\"}"));

        assertTrue(result.isAccepted());
        assertEquals("401", result.getExternalRef());
        verify(contentTaskService, never()).create(any());
    }

    @Test
    @DisplayName("内容域自己的业务拒绝（产品不存在等）原样回，不当系统故障")
    void domainServiceRejectionIsPassedThrough() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(contentTaskService.create(any()))
            .thenThrow(new ServiceException("产品不存在：123"));

        AigScenarioFlowResult result = port.dispatch(request(
            "{\"deliverableType\":\"ECOM_DETAIL\",\"productId\":123}"));

        assertFalse(result.isAccepted());
        assertEquals("产品不存在：123", result.getMessage());
    }

    @Test
    @DisplayName("缺 taskName 时用可读兜底名（不会建出无名任务）")
    void missingNameFallsBackToReadableName() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(contentTaskService.create(any())).thenReturn(401L);

        port.dispatch(request("{\"deliverableType\":\"MAIN_IMAGE\"}"));

        ArgumentCaptor<ContentTaskBo> captor = ArgumentCaptor.forClass(ContentTaskBo.class);
        verify(contentTaskService).create(captor.capture());
        assertTrue(captor.getValue().getTaskName().contains("COMMERCE"),
            captor.getValue().getTaskName());
        assertTrue(captor.getValue().getTaskName().contains("88"), captor.getValue().getTaskName());
    }

    private static AigScenarioFlowRequest request(String snapshotJson) {
        AigScenarioFlowRequest request = new AigScenarioFlowRequest();
        request.setTaskId(88L);
        request.setTaskNo("AIG-88");
        request.setScenarioCode("COMMERCE");
        request.setScenarioVersion("1.0.0");
        request.setAdapter("CONTENT_EXISTING_FLOW");
        request.setDataLevel("INTERNAL");
        request.setRequesterId(9L);
        request.setSnapshotJson(snapshotJson);
        return request;
    }

}
