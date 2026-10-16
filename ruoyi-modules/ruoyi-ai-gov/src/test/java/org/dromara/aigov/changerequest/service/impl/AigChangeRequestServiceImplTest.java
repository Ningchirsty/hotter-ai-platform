package org.dromara.aigov.changerequest.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.changerequest.domain.AigChangeRequest;
import org.dromara.aigov.changerequest.domain.bo.AigChangeRequestBo;
import org.dromara.aigov.changerequest.domain.vo.AigChangeRequestVo;
import org.dromara.aigov.changerequest.mapper.AigChangeRequestMapper;
import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.aigov.mapper.AigRoutePolicyMapper;
import org.dromara.aigov.service.IAigRoutePolicyService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 高风险变更单（Q1C 完整版）：申请 → 批准 → 生效记录。
 *
 * <p><b>守的是三条硬规矩</b>：①申请人不得自批；②批准即执行，但执行前必须做乐观并发校验
 * （现场被改过就置 CONFLICTED，<b>绝不覆盖</b>）；③冲突/失败要**留下那一行**而不是抛异常
 * （抛异常会把记录回滚掉）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigChangeRequestServiceImplTest {

    private static final long REQUEST_ID = 9001L;
    private static final long POLICY_ID = 7777L;
    private static final long REQUESTER = 1001L;
    private static final long APPROVER = 1002L;

    private AigChangeRequestMapper changeRequestMapper;
    private AigRoutePolicyMapper routePolicyMapper;
    private IAigRoutePolicyService routePolicyService;
    private AigChangeRequestServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigChangeRequest.class);
        TableInfoHelper.initTableInfo(assistant, AigRoutePolicy.class);
    }

    @BeforeEach
    void setUp() {
        changeRequestMapper = mock(AigChangeRequestMapper.class);
        routePolicyMapper = mock(AigRoutePolicyMapper.class);
        routePolicyService = mock(IAigRoutePolicyService.class);
        service = new AigChangeRequestServiceImpl(changeRequestMapper, routePolicyMapper, routePolicyService,
            JsonMapper.builder().build());
    }

    /**
     * 现场那一行策略。
     *
     * @param allowExternal 是否允许外发
     * @return 策略行
     */
    private static AigRoutePolicy policy(String allowExternal) {
        AigRoutePolicy row = new AigRoutePolicy();
        row.setPolicyId(POLICY_ID);
        row.setCapabilityCode("image_generation");
        row.setDataLevel("INTERNAL");
        row.setAllowExternal(allowExternal);
        row.setPreferredDeployment("LOCAL");
        row.setRequireApproval("N");
        row.setFallbackToManual("Y");
        row.setStatus("0");
        return row;
    }

    /**
     * 一张待批准的变更单（把 INTERNAL 的外发从 N 改成 Y —— 典型高风险改动）。
     *
     * @return 变更单
     */
    private static AigChangeRequest pendingRequest() {
        AigChangeRequest row = new AigChangeRequest();
        row.setChangeRequestId(REQUEST_ID);
        row.setCapabilityCode("image_generation");
        row.setChangeType("POLICY_UPDATE");
        row.setTargetRef(POLICY_ID);
        row.setBeforeJson("{\"dataLevel\":\"INTERNAL\",\"allowExternal\":\"N\","
            + "\"preferredDeployment\":\"LOCAL\",\"requireApproval\":\"N\","
            + "\"fallbackToManual\":\"Y\",\"status\":\"0\"}");
        row.setAfterJson("{\"dataLevel\":\"INTERNAL\",\"allowExternal\":\"Y\","
            + "\"preferredDeployment\":\"LOCAL\",\"requireApproval\":\"N\","
            + "\"fallbackToManual\":\"Y\",\"status\":\"0\"}");
        row.setStatus("PENDING");
        row.setRequesterId(REQUESTER);
        row.setRequesterName("zhangsan");
        return row;
    }

    @Test
    @DisplayName("★ 申请：存下前后快照（申请时的那一份现场要留证，批准时靠它做并发校验）")
    void submitKeepsBeforeAndAfter() {
        when(routePolicyMapper.selectById(POLICY_ID)).thenReturn(policy("N"));
        when(changeRequestMapper.insert(any(AigChangeRequest.class))).thenAnswer(invocation -> {
            invocation.<AigChangeRequest>getArgument(0).setChangeRequestId(REQUEST_ID);
            return 1;
        });

        AigChangeRequestBo bo = new AigChangeRequestBo();
        bo.setCapabilityCode("image_generation");
        bo.setChangeType("POLICY_UPDATE");
        bo.setTargetRef(POLICY_ID);
        bo.setAllowExternal("Y");
        bo.setReason("业务确认可以外发");

        Long id = service.submit(bo, REQUESTER, "zhangsan");

        assertEquals(REQUEST_ID, id);
        ArgumentCaptor<AigChangeRequest> captor = ArgumentCaptor.forClass(AigChangeRequest.class);
        verify(changeRequestMapper).insert(captor.capture());
        AigChangeRequest saved = captor.getValue();
        assertEquals("PENDING", saved.getStatus());
        assertTrue(saved.getBeforeJson().contains("\"allowExternal\":\"N\""), saved.getBeforeJson());
        assertTrue(saved.getAfterJson().contains("\"allowExternal\":\"Y\""), saved.getAfterJson());
        // 没给的列沿用当前行（PATCH 语义）：只改外发，别把数据等级清空
        assertTrue(saved.getAfterJson().contains("\"dataLevel\":\"INTERNAL\""), saved.getAfterJson());
    }

    @Test
    @DisplayName("★ 申请人不得自批（一张单子要两个人才能成立）")
    void requesterCannotApproveOwnRequest() {
        when(changeRequestMapper.selectById(REQUEST_ID)).thenReturn(pendingRequest());

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.approve(REQUEST_ID, null, REQUESTER, "zhangsan"));

        assertTrue(e.getMessage().contains("不得批准自己"), e.getMessage());
        verify(routePolicyService, never()).update(any());
        verify(changeRequestMapper, never()).updateById(any(AigChangeRequest.class));
    }

    @Test
    @DisplayName("★ 现场被改过 → 置 CONFLICTED、写清原因、**不执行**、也不抛异常（记录必须留下）")
    void conflictIsRecordedNotThrown() {
        when(changeRequestMapper.selectById(REQUEST_ID)).thenReturn(pendingRequest());
        // 现场已经是 Y（别人先改过了），与申请时的 N 不一致
        when(routePolicyMapper.selectById(POLICY_ID)).thenReturn(policy("Y"));

        AigChangeRequestVo result = service.approve(REQUEST_ID, "同意", APPROVER, "lisi");

        assertEquals("CONFLICTED", result.getStatus());
        assertNotNull(result.getResultRemark());
        assertTrue(result.getResultRemark().contains("allowExternal"), result.getResultRemark());
        verify(routePolicyService, never()).update(any());
        verify(routePolicyService, never()).remove(any());
    }

    @Test
    @DisplayName("★ 批准成功 → 走既有服务方法执行（闸门因此仍生效）并落生效时间")
    void approveAppliesThroughExistingService() {
        when(changeRequestMapper.selectById(REQUEST_ID))
            .thenReturn(pendingRequest())          // requirePending
            .thenReturn(appliedCopy());            // 回读视图
        when(routePolicyMapper.selectById(POLICY_ID)).thenReturn(policy("N"));

        AigChangeRequestVo result = service.approve(REQUEST_ID, "同意", APPROVER, "lisi");

        ArgumentCaptor<org.dromara.aigov.domain.bo.AigRoutePolicyBo> captor =
            ArgumentCaptor.forClass(org.dromara.aigov.domain.bo.AigRoutePolicyBo.class);
        verify(routePolicyService).update(captor.capture());
        assertEquals(POLICY_ID, captor.getValue().getPolicyId());
        assertEquals("Y", captor.getValue().getAllowExternal(), "目标值必须是单子里的 after");
        assertEquals(APPROVER, result.getApproverId());
        assertNotNull(result.getAppliedAt());
    }

    /**
     * 已生效的单子（模拟回读）。
     *
     * @return 变更单
     */
    private static AigChangeRequest appliedCopy() {
        AigChangeRequest row = pendingRequest();
        row.setStatus("APPLIED");
        row.setApproverId(APPROVER);
        row.setApproverName("lisi");
        row.setAppliedAt(java.time.LocalDateTime.now());
        return row;
    }

    @Test
    @DisplayName("驳回必须写理由（不然申请人不知道该改什么）")
    void rejectNeedsReason() {
        when(changeRequestMapper.selectById(REQUEST_ID)).thenReturn(pendingRequest());

        assertThrows(ServiceException.class, () -> service.reject(REQUEST_ID, "  ", APPROVER, "lisi"));
        verify(changeRequestMapper, never()).updateById(any(AigChangeRequest.class));
    }

    @Test
    @DisplayName("撤回只有申请人本人可以（服务端强制，不靠页面藏按钮）")
    void cancelOnlyByRequester() {
        when(changeRequestMapper.selectById(REQUEST_ID)).thenReturn(pendingRequest());

        assertThrows(ServiceException.class, () -> service.cancel(REQUEST_ID, APPROVER));

        when(changeRequestMapper.updateById(any(AigChangeRequest.class))).thenReturn(1);
        service.cancel(REQUEST_ID, REQUESTER);
        ArgumentCaptor<AigChangeRequest> captor = ArgumentCaptor.forClass(AigChangeRequest.class);
        verify(changeRequestMapper).updateById(captor.capture());
        assertEquals("CANCELLED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("已处理过的单子不能重复操作（避免批两次）")
    void finishedRequestCannotBeHandledAgain() {
        AigChangeRequest applied = pendingRequest();
        applied.setStatus("APPLIED");
        when(changeRequestMapper.selectById(REQUEST_ID)).thenReturn(applied);

        assertThrows(ServiceException.class, () -> service.approve(REQUEST_ID, null, APPROVER, "lisi"));
        assertThrows(ServiceException.class, () -> service.reject(REQUEST_ID, "不同意", APPROVER, "lisi"));
    }
}
