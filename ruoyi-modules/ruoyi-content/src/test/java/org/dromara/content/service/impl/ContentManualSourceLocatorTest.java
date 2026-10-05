package org.dromara.content.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.CpFactSnapshot;
import org.dromara.content.domain.CpInteractionCard;
import org.dromara.content.domain.CpTask;
import org.dromara.content.domain.CpTaskFile;
import org.dromara.content.domain.bo.ContentCardResolveBo;
import org.dromara.content.domain.bo.ContentFactManualBo;
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
 * 人工事实的「出处」必须指到一份具体资料（内测 S19 → C7-b 的回归钉）。
 *
 * <p><b>演进过程本身值得记</b>：内测发现开工包里 5/7 条事实出处为空（S19）→ 加了"出处必填"
 * （C7）→ 但自由文本可以被填成 {@code -}，形式满足、追溯失效。
 * 所以 C7-b 把它改成"**必选本任务的一份资料** + 可选位置"：
 * 结构化那一半能被核对，自由文本那一半本来就没有稳定结构。</p>
 *
 * <p><b>为什么两条路径都要钉</b>：产线里有<b>两个</b>入口会把人工填的值直接落 {@code CONFIRMED}——
 * 内容任务页的「手工录入事实」与互动确认卡上的「填写其他值」。它们看着是两个功能，
 * 对下游完全等价（开工包把出处原样交出去）。只改一条 = 等于没改。</p>
 *
 * <p>钉四件事：<b>没选资料要拒</b>、<b>选了别人的任务的资料也要拒</b>（跨任务引用既可能是误操作、
 * 也可能是越权读取）、<b>允许时结构化字段与可读出处都要写进去</b>
 * （只加校验忘了 set，界面一切正常而库里照样是空的）。</p>
 *
 * @author content
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContentManualSourceLocatorTest {

    private static final long TASK_ID = 9001L;
    private static final long OTHER_TASK_ID = 9002L;
    private static final long CARD_ID = 7001L;
    private static final long FILE_ID = 5001L;
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

    /** 本任务的一份资料 */
    private CpTaskFile file(long taskId) {
        CpTaskFile file = new CpTaskFile();
        file.setFileId(FILE_ID);
        file.setTaskId(taskId);
        file.setFileName("产品参数表 V2.xlsx");
        return file;
    }

    private ContentFactManualBo manualBo(Long sourceFileId, String locator) {
        ContentFactManualBo bo = new ContentFactManualBo();
        bo.setTaskId(TASK_ID);
        bo.setFieldCode(FIELD_CODE);
        bo.setValue("趣往");
        bo.setSourceFileId(sourceFileId);
        bo.setSourceLocator(locator);
        return bo;
    }

    @Test
    @DisplayName("手工录入：没选资料 → 拒绝，且不落库")
    void manualWithoutSourceFileIsRejected() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> factService.addManual(manualBo(null, "第 3 行")));

        assertTrue(ex.getMessage().contains("出处"),
            "报错要直说缺的是出处，实际：" + ex.getMessage());
        verifyNoInteractions(factSnapshotMapper);
    }

    @Test
    @DisplayName("手工录入：选了别的任务的资料 → 拒绝（跨任务引用既可能是误操作也可能是越权）")
    void manualRejectsForeignFile() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());
        when(taskFileMapper.selectById(FILE_ID)).thenReturn(file(OTHER_TASK_ID));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> factService.addManual(manualBo(FILE_ID, null)));

        assertTrue(ex.getMessage().contains("不属于本任务"), ex.getMessage());
        verifyNoInteractions(factSnapshotMapper);
    }

    @Test
    @DisplayName("手工录入：有资料 → 结构化出处与可读出处都真的写进了那一行")
    void manualPersistsStructuredOrigin() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());
        when(taskFileMapper.selectById(FILE_ID)).thenReturn(file(TASK_ID));
        when(gateRuleService.allEnabledFieldCodes()).thenReturn(Set.of(FIELD_CODE));
        when(factSnapshotMapper.selectList(any())).thenReturn(List.of());

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(USER_ID);
            factService.addManual(manualBo(FILE_ID, " 第 3 行 "));
        }

        ArgumentCaptor<CpFactSnapshot> captor = ArgumentCaptor.forClass(CpFactSnapshot.class);
        verify(factSnapshotMapper).insert(captor.capture());
        CpFactSnapshot saved = captor.getValue();
        // 结构化那一半：能被核对（文件归属已校验过）
        assertEquals(FILE_ID, saved.getSourceFileId(), "只加校验、忘了 set 进实体，库里照样是空的");
        // 可读那一半：开工包交给下游的就是这个字符串，只有位置没有文件名等于没说清出处
        assertEquals("产品参数表 V2.xlsx · 第 3 行", saved.getSourceLocator());
        assertEquals(ContentFactConfirmStatusEnum.CONFIRMED.getCode(), saved.getConfirmStatus(),
            "手工录入即视为已确认——这正是它必须要求出处的理由");
    }

    @Test
    @DisplayName("互动卡「填写其他值」：没选资料 → 拒绝，且不落库")
    void cardOtherWithoutSourceFileIsRejected() {
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
            "「填写其他值」与手工录入等价，出处同样必选，实际：" + ex.getMessage());
        verifyNoInteractions(factSnapshotMapper);
    }

    @Test
    @DisplayName("互动卡「填写其他值」：有资料 → 结构化出处也落库（两条入口同款）")
    void cardOtherPersistsStructuredOrigin() {
        CpInteractionCard card = new CpInteractionCard();
        card.setCardId(CARD_ID);
        card.setTaskId(TASK_ID);
        card.setFieldCode(FIELD_CODE);
        card.setStatus(ContentCardStatusEnum.PENDING.getCode());
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(taskFileMapper.selectById(FILE_ID)).thenReturn(file(TASK_ID));
        when(factSnapshotMapper.selectList(any())).thenReturn(List.of());

        ContentCardResolveBo bo = new ContentCardResolveBo();
        bo.setCardId(CARD_ID);
        bo.setOption("OTHER");
        bo.setValue("趣往");
        bo.setSourceFileId(FILE_ID);
        bo.setSourceLocator("第 2 页");

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(USER_ID);
            cardService.resolve(bo);
        }

        ArgumentCaptor<CpFactSnapshot> captor = ArgumentCaptor.forClass(CpFactSnapshot.class);
        verify(factSnapshotMapper).insert(captor.capture());
        assertEquals(FILE_ID, captor.getValue().getSourceFileId());
        assertEquals("产品参数表 V2.xlsx · 第 2 页", captor.getValue().getSourceLocator());
    }
}
