package org.dromara.content.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.CpFactSnapshot;
import org.dromara.content.domain.CpInteractionCard;
import org.dromara.content.domain.CpTask;
import org.dromara.content.domain.bo.ContentCardResolveBo;
import org.dromara.content.enums.ContentCardStatusEnum;
import org.dromara.content.enums.ContentFactConfirmStatusEnum;
import org.dromara.content.mapper.CpFactSnapshotMapper;
import org.dromara.content.mapper.CpInteractionCardMapper;
import org.dromara.content.mapper.CpTaskFileMapper;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.content.service.IContentGateRuleService;
import org.dromara.content.service.IContentTaskGateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 人工录入事实必须带出处（内测 S19 / C7 的回归钉）。
 *
 * <p><b>为什么两条路径都要钉</b>：产线里有<b>两个</b>入口会把"人手填的值"直接落成
 * {@code CONFIRMED}：内容任务页的「手工录入事实」，和互动确认卡上的「填写其他值」。
 * 它们看起来是两个功能，对下游完全等价——开工包会把事实连同 {@code sourceLocator}
 * 一起交给设计侧，所以只要有一条漏了出处，开工包里就照样出现无法追溯的行
 * （内测实测：7 条事实有 5 条出处为空）。</p>
 *
 * <p>钉的是"必填"这个行为本身：拒绝时要能说清为什么；允许时要真的把出处写进那一行——
 * 只加一个必填校验、却忘了 set 进实体，界面上一切正常而库里照样是空的。</p>
 *
 * @author content
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContentManualSourceLocatorTest {

    private static final long TASK_ID = 9001L;
    private static final long CARD_ID = 7001L;
    private static final long USER_ID = 1761100000000000001L;
    private static final String FIELD_CODE = "product_name";

    @Mock
    private CpFactSnapshotMapper factSnapshotMapper;
    @Mock
    private CpTaskMapper taskMapper;
    @Mock
    private CpTaskFileMapper taskFileMapper;
    @Mock
    private IContentTaskGateService taskGateService;
    @Mock
    private IContentGateRuleService gateRuleService;
    @InjectMocks
    private ContentFactServiceImpl factService;

    @Mock
    private CpInteractionCardMapper cardMapper;
    @InjectMocks
    private ContentCardServiceImpl cardService;

    private CpTask task() {
        CpTask task = new CpTask();
        task.setTaskId(TASK_ID);
        task.setDeliverableType("ECOM_DETAIL");
        return task;
    }

    @Test
    @DisplayName("手工录入：没有出处 → 拒绝，且不落库")
    void manualWithoutSourceLocatorIsRejected() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> factService.addManual(TASK_ID, FIELD_CODE, "趣往", "人工录入", null));

        assertTrue(ex.getMessage().contains("出处"),
            "报错要直说缺的是出处，实际：" + ex.getMessage());
        verifyNoInteractions(factSnapshotMapper);
    }

    @Test
    @DisplayName("手工录入：有出处 → 出处真的写进了那一行")
    void manualPersistsSourceLocator() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());
        when(gateRuleService.allEnabledFieldCodes()).thenReturn(Set.of(FIELD_CODE));
        when(factSnapshotMapper.selectList(any())).thenReturn(List.of());

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(USER_ID);
            factService.addManual(TASK_ID, FIELD_CODE, "趣往", "人工录入", "产品参数表 V2 第 3 行");
        }

        ArgumentCaptor<CpFactSnapshot> captor = ArgumentCaptor.forClass(CpFactSnapshot.class);
        verify(factSnapshotMapper).insert(captor.capture());
        CpFactSnapshot saved = captor.getValue();
        assertEquals("产品参数表 V2 第 3 行", saved.getSourceLocator(),
            "只加必填校验、忘了 set 进实体，库里照样是空的");
        assertEquals(ContentFactConfirmStatusEnum.CONFIRMED.getCode(), saved.getConfirmStatus(),
            "手工录入即视为已确认——这正是它必须要求出处的理由");
    }

    @Test
    @DisplayName("互动卡「填写其他值」：没有出处 → 拒绝，且不落库")
    void cardOtherWithoutSourceLocatorIsRejected() {
        CpInteractionCard card = new CpInteractionCard();
        card.setCardId(CARD_ID);
        card.setTaskId(TASK_ID);
        card.setFieldCode(FIELD_CODE);
        card.setStatus(ContentCardStatusEnum.PENDING.getCode());
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);

        ContentCardResolveBo bo = new ContentCardResolveBo();
        bo.setCardId(CARD_ID);
        bo.setOption("OTHER");
        bo.setValue("趣往");

        ServiceException ex = assertThrows(ServiceException.class, () -> cardService.resolve(bo));
        assertTrue(ex.getMessage().contains("出处"),
            "「填写其他值」与手工录入等价，出处同样必填，实际：" + ex.getMessage());
        verifyNoInteractions(factSnapshotMapper);
    }
}
