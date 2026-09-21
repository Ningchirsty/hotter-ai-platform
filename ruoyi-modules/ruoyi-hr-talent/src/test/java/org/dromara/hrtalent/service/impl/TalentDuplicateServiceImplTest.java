package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.hrtalent.domain.bo.talent.DuplicateConfirmBo;
import org.dromara.hrtalent.domain.bo.talent.DuplicateIgnoreBo;
import org.dromara.hrtalent.domain.bo.talent.TalentDuplicateQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentMergeBo;
import org.dromara.hrtalent.domain.bo.talent.TalentMergeFields;
import org.dromara.hrtalent.domain.entity.TalentDuplicateCase;
import org.dromara.hrtalent.domain.entity.TalentMergeLog;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.entity.RecruitSensitiveAudit;
import org.dromara.hrtalent.domain.vo.talent.TalentDuplicateCaseVo;
import org.dromara.hrtalent.domainservice.ITalentMergeRelationMigrator;
import org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator;
import org.dromara.hrtalent.domainservice.TalentMergeRelationSnapshot;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.enums.DuplicateStatusEnum;
import org.dromara.hrtalent.enums.TalentStatusEnum;
import org.dromara.hrtalent.enums.TalentVisibilityTypeEnum;
import org.dromara.hrtalent.event.TalentMergedEvent;
import org.dromara.hrtalent.mapper.TalentDuplicateCaseMapper;
import org.dromara.hrtalent.mapper.TalentMergeLogMapper;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.mapper.RecruitSensitiveAuditMapper;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重复人才治理与合并的领域规则单元测试（SPEC-P4 §2.5、设计文档 §8.18、§9.6、§11.1）。
 *
 * <p><b>覆盖的硬性规则</b>（对应 SPEC-P4 §4 自检清单）：</p>
 * <ol>
 *     <li>非集团人才管理员<b>不得</b>执行合并；</li>
 *     <li>乐观锁版本冲突必须被拒绝（{@code HR_TALENT_004}）；</li>
 *     <li>合并后被合并主档为 {@code merged} 且写入 {@code merged_to_id}；</li>
 *     <li>完整合并快照（字段决策 + 关系迁移数量）被写入 {@code hr_talent_merge_log}；</li>
 *     <li>合并过程中<b>不出现任何物理删除</b>（替身记录全部收到的方法名并断言无 delete）；</li>
 *     <li>关系迁移只经 {@code snapshot}/{@code migrate} 两个入口，迁移前后各取一次数量快照。</li>
 * </ol>
 *
 * <p><b>说明</b>：Mapper、事件发布器与审计记录器均用 JDK 动态代理手工构造，不依赖 Mockito
 * （本机 JVM 不允许 Mockito 自附加 Byte Buddy Agent，参见 {@code RecruitJobServiceImplTest}）。
 * 权限判定与登录态读取通过覆写 {@link TalentDuplicateServiceImpl#isMergeAllowed()} /
 * {@code currentUserId()} 控制，使「集团管理员 / 非集团管理员」两条分支都真实走到服务逻辑。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class TalentDuplicateServiceImplTest {

    /**
     * 保留主档ID。
     */
    private static final Long KEEP_ID = 1001L;

    /**
     * 被合并主档ID。
     */
    private static final Long MERGED_ID = 1002L;

    /**
     * 疑似重复案件ID。
     */
    private static final Long CASE_ID = 2001L;

    /**
     * 内存中的人才主档，按主键索引。
     */
    private final Map<Long, TalentProfile> profileStore = new HashMap<>();

    /**
     * 内存中的疑似重复案件，按主键索引。
     */
    private final Map<Long, TalentDuplicateCase> caseStore = new HashMap<>();

    /**
     * 写入的合并日志。
     */
    private final List<TalentMergeLog> mergeLogs = new ArrayList<>();

    /**
     * 事件发布记录。
     */
    private final List<Object> publishedEvents = new ArrayList<>();

    /**
     * 审计记录。
     */
    private final List<AuditRow> audits = new ArrayList<>();

    /**
     * 主档 Mapper 替身收到的全部调用方法名（用于断言不存在物理删除）。
     */
    private final List<String> profileMapperCalls = new ArrayList<>();

    /**
     * 关系迁移替身收到的调用方法名。
     */
    private final List<String> migratorCalls = new ArrayList<>();

    /**
     * 关系迁移替身：被合并主档迁移前持有的关系数量。
     */
    private final Map<String, Long> beforeCounts = new LinkedHashMap<>();

    /**
     * 被合并主档 {@code updateById} 应返回的行数队列（用于构造并发版本冲突）。
     */
    private final List<Integer> mergedUpdateRows = new ArrayList<>();

    /**
     * 主档 Mapper 替身收到的「针对被合并主档的 updateById」次数（诊断用）。
     */
    private int updateByIdCallsForMerged;

    /**
     * 被测服务（测试子类）。
     */
    private TestableService service;

    @BeforeEach
    void setUp() {
        profileStore.clear();
        caseStore.clear();
        mergeLogs.clear();
        publishedEvents.clear();
        audits.clear();
        profileMapperCalls.clear();
        migratorCalls.clear();
        beforeCounts.clear();
        mergedUpdateRows.clear();
        updateByIdCallsForMerged = 0;
        beforeCounts.put(TalentMergeRelationMigrator.TABLE_APPLICATION, 1L);
        beforeCounts.put(TalentMergeRelationMigrator.TABLE_RESUME, 2L);

        service = new TestableService(
            caseMapperStub(),
            mergeLogMapperStub(),
            profileMapperStub(),
            migratorStub(),
            new TalentScopeDomainService(null),
            auditRecorderStub(),
            eventPublisherStub(),
            profileStore);
    }

    /* ------------------------------------------------------------------ 测试用例 ------------------------------------------------------------------ */

    @Test
    @DisplayName("非集团人才管理员不得执行人才合并（且不产生任何写操作与快照）")
    void shouldRejectMergeWhenNotGroupLevelAdmin() {
        prepareMergeableCase();
        service.groupLevelAdmin = false;

        ServiceException ex = assertThrows(ServiceException.class, () -> service.merge(CASE_ID, mergeBo(null)));

        assertEquals("仅集团人才管理员可以执行人才合并", ex.getMessage());
        assertTrue(mergeLogs.isEmpty(), "被拒绝的合并不得写入合并快照");
        assertTrue(migratorCalls.isEmpty(), "被拒绝的合并不得触发任何关系迁移");
        assertTrue(publishedEvents.isEmpty(), "被拒绝的合并不得发布事件");
        assertEquals(1, audits.size(), "被拒绝的合并必须留下审计记录");
        assertEquals(SensitiveAuditRecorder.RESULT_DENIED, audits.get(0).result);
        // 被合并主档必须保持原状
        assertEquals(TalentStatusEnum.ACTIVE.getCode(), profileStore.get(MERGED_ID).getTalentStatus());
        assertNull(profileStore.get(MERGED_ID).getMergedToId());
    }

    @Test
    @DisplayName("版本冲突时拒绝合并并提示 HR_TALENT_004")
    void shouldRejectMergeOnVersionConflict() {
        prepareMergeableCase();
        // 被合并主档的乐观锁更新失败（0 行），模拟并发修改
        mergedUpdateRows.add(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.merge(CASE_ID, mergeBo(null)));

        assertEquals(HrTalentErrorCode.MSG_HR_TALENT_004, ex.getMessage());
        assertEquals(1, updateByIdCallsForMerged, "版本冲突用例应只对被合并主档发起一次 updateById");
        assertTrue(mergeLogs.isEmpty(), "版本冲突时不得写入合并快照");
        assertTrue(publishedEvents.isEmpty(), "版本冲突时不得发布合并事件");
        assertEquals(TalentStatusEnum.ACTIVE.getCode(), profileStore.get(MERGED_ID).getTalentStatus());
    }

    @Test
    @DisplayName("入参版本号与主档版本不一致时在写库前拒绝")
    void shouldRejectStaleVersionBeforeAnyWrite() {
        prepareMergeableCase();

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.merge(CASE_ID, mergeBo(99)));

        assertEquals(HrTalentErrorCode.MSG_HR_TALENT_004, ex.getMessage());
        assertTrue(migratorCalls.isEmpty(), "陈旧版本必须在关系迁移之前被拒绝");
        assertTrue(mergeLogs.isEmpty());
    }

    @Test
    @DisplayName("合并成功后：被合并主档为 merged 且写入 merged_to_id，保留主档应用冲突字段决策")
    void shouldMarkMergedAndApplyFieldDecisions() {
        prepareMergeableCase();
        TalentMergeBo bo = mergeBo(null);
        // 冲突字段：姓名/当前公司/当前城市 取自被合并主档，期望岗位保留保留主档
        bo.setFieldDecisions(List.of(
            decision(TalentMergeFields.NAME, "merged"),
            decision(TalentMergeFields.CURRENT_COMPANY, "merged"),
            decision(TalentMergeFields.EXPECTED_POSITION, "keep"),
            decision(TalentMergeFields.CURRENT_CITY, "merged")));

        Long mergeId = service.merge(CASE_ID, bo);

        assertNotNull(mergeId, "合并必须返回合并日志ID");
        TalentProfile merged = profileStore.get(MERGED_ID);
        assertEquals(TalentStatusEnum.MERGED.getCode(), merged.getTalentStatus(), "被合并主档必须标记为 merged");
        assertEquals(KEEP_ID, merged.getMergedToId(), "被合并主档必须保存目标主档ID");

        TalentProfile keep = profileStore.get(KEEP_ID);
        assertEquals("张三丰", keep.getName(), "姓名应取被合并主档的值");
        assertEquals("旧公司", keep.getCurrentCompany(), "当前公司应取被合并主档的值");
        assertEquals("Java 工程师", keep.getExpectedPosition(), "期望岗位应保留保留主档的值");
        assertEquals("上海", keep.getCurrentCity(), "当前城市应取被合并主档的值");
        // 无冲突字段补全：保留主档学历为空时由被合并主档补全，避免合并丢资料
        assertEquals("本科", keep.getHighestEducation(), "保留主档为空的字段应由被合并主档补全");
    }

    @Test
    @DisplayName("合并成功后写入完整合并快照（字段决策 + 关系迁移数量）并发布事件")
    void shouldWriteFullMergeSnapshot() {
        prepareMergeableCase();
        TalentMergeBo bo = mergeBo(null);
        bo.setFieldDecisions(List.of(decision(TalentMergeFields.NAME, "merged")));

        service.merge(CASE_ID, bo);

        assertEquals(1, mergeLogs.size(), "必须写入且只写入一条合并快照");
        TalentMergeLog log = mergeLogs.get(0);
        assertEquals(KEEP_ID, log.getKeepTalentId());
        assertEquals(MERGED_ID, log.getMergedTalentId());
        assertEquals("疑似同一人（电话哈希命中）", log.getMergeReason());
        assertNotNull(log.getOperateTime(), "快照必须记录操作时间");
        assertNotNull(log.getFieldDecisionJson(), "必须写入字段决策快照");
        assertNotNull(log.getRelationCountJson(), "必须写入关系迁移数量快照");
        assertTrue(log.getFieldDecisionJson().contains("name"), "字段决策快照应包含被决策的字段名");
        assertTrue(log.getFieldDecisionJson().contains("merged"), "字段决策快照应包含取值来源");
        assertTrue(log.getRelationCountJson().contains("before"), "关系快照应包含迁移前数量");
        assertTrue(log.getRelationCountJson().contains("moved"), "关系快照应包含实际迁移行数");
        assertTrue(log.getRelationCountJson().contains("after"), "关系快照应包含迁移后数量");
        assertTrue(log.getRelationCountJson().contains("\"complete\":true"),
            "迁移后残留为 0，快照应标记 complete=true");
        // 操作人必须进入合并快照（关系表 update_by 由 migrate 的同一 operatorId 回写，已在替身中断言）
        assertEquals(Long.valueOf(9L), log.getOperatorId(), "合并快照必须记录操作人");
        // 关系确实被转移，且迁移前后各取一次快照
        assertTrue(migratorCalls.contains("migrate"), "必须调用关系迁移");
        assertEquals(2, migratorCalls.stream().filter("snapshot"::equals).count(),
            "关系数量必须在迁移前后各取一次快照");
        // 审计与事件
        assertEquals(1, audits.size(), "合并必须写审计记录");
        assertEquals(SensitiveAuditRecorder.RESULT_SUCCESS, audits.get(0).result);
        assertEquals(1, publishedEvents.size(), "合并必须发布 TalentMergedEvent");
        assertEquals(TalentMergedEvent.class, publishedEvents.get(0).getClass());
        assertEquals(KEEP_ID, ((TalentMergedEvent) publishedEvents.get(0)).keepTalentId());
        assertEquals(MERGED_ID, ((TalentMergedEvent) publishedEvents.get(0)).mergedTalentId());
        // 相关疑似重复案件被置为已合并
        assertEquals(DuplicateStatusEnum.MERGED.getCode(), caseStore.get(CASE_ID).getStatus());
    }

    @Test
    @DisplayName("合并全过程不出现任何物理删除调用")
    void shouldNeverPhysicallyDelete() {
        prepareMergeableCase();

        service.merge(CASE_ID, mergeBo(null));

        List<String> deleteCalls = profileMapperCalls.stream()
            .filter(name -> name.toLowerCase().startsWith("delete"))
            .toList();
        assertTrue(deleteCalls.isEmpty(), "合并过程中不得出现物理删除，实际调用：" + deleteCalls);
        List<String> migratorDeleteCalls = migratorCalls.stream()
            .filter(name -> name.toLowerCase().contains("delete"))
            .toList();
        assertTrue(migratorDeleteCalls.isEmpty(), "关系迁移不得包含删除能力，实际调用：" + migratorDeleteCalls);
        assertEquals(List.of("snapshot", "migrate", "snapshot"), migratorCalls,
            "合并只应调用关系迁移的 snapshot/migrate，不应调用其他（尤其删除类）方法");
    }

    @Test
    @DisplayName("确认疑似重复：同一人置 confirmed（已确认待合并，脱离待处理队列），非同一人终结为 not_same")
    void shouldConfirmDuplicateCase() {
        prepareMergeableCase();
        DuplicateConfirmBo bo = new DuplicateConfirmBo();
        bo.setSamePerson(Boolean.TRUE);
        bo.setReason("电话哈希一致，确认为同一人");

        service.confirm(CASE_ID, bo);

        TalentDuplicateCase duplicateCase = caseStore.get(CASE_ID);
        assertEquals(DuplicateStatusEnum.CONFIRMED.getCode(), duplicateCase.getStatus(),
            "确认同一人必须置 confirmed，与 pending（待人工判定）区分");
        // 已确认案件不得再出现在「待处理」队列（按 status = pending 过滤）
        assertFalse(DuplicateStatusEnum.PENDING.getCode().equals(duplicateCase.getStatus()),
            "已确认案件不得落在待处理状态");
        assertEquals(9L, duplicateCase.getHandledBy(), "必须记录确认人");
        assertNotNull(duplicateCase.getHandledTime(), "必须记录确认时间");
        assertTrue(duplicateCase.getRemark().contains("电话哈希一致"), "必须保留确认依据");
        assertEquals(1, audits.size());

        // 已确认的案件不能被重复确认（状态已非 pending）
        ServiceException repeated = assertThrows(ServiceException.class, () -> service.confirm(CASE_ID, bo));
        assertTrue(repeated.getMessage().contains("已处理"));
    }

    @Test
    @DisplayName("确认非同一人时终结为 not_same")
    void shouldConfirmNotSamePerson() {
        prepareMergeableCase();
        DuplicateConfirmBo notSame = new DuplicateConfirmBo();
        notSame.setSamePerson(Boolean.FALSE);
        notSame.setReason("同名不同人");

        service.confirm(CASE_ID, notSame);

        assertEquals(DuplicateStatusEnum.NOT_SAME.getCode(), caseStore.get(CASE_ID).getStatus());
        assertFalse(DuplicateStatusEnum.PENDING.getCode().equals(caseStore.get(CASE_ID).getStatus()));
    }

    @Test
    @DisplayName("已确认待合并的案件在合并后必须落到 merged（不能停留在 confirmed）")
    void shouldCloseConfirmedCaseAfterMerge() {
        prepareMergeableCase();
        DuplicateConfirmBo bo = new DuplicateConfirmBo();
        bo.setSamePerson(Boolean.TRUE);
        bo.setReason("确认为同一人");
        service.confirm(CASE_ID, bo);
        assertEquals(DuplicateStatusEnum.CONFIRMED.getCode(), caseStore.get(CASE_ID).getStatus());

        service.merge(CASE_ID, mergeBo(null));

        assertEquals(DuplicateStatusEnum.MERGED.getCode(), caseStore.get(CASE_ID).getStatus(),
            "confirmed 属未终结状态，合并后必须闭环为 merged");
    }

    @Test
    @DisplayName("忽略疑似重复：填写原因后转为 ignored / not_same，且不改动人才资料")
    void shouldIgnoreDuplicateCase() {
        prepareMergeableCase();
        DuplicateIgnoreBo bo = new DuplicateIgnoreBo();
        bo.setReason("信息不足，暂不处理");
        bo.setNotSamePerson(Boolean.FALSE);

        service.ignore(CASE_ID, bo);

        assertEquals(DuplicateStatusEnum.IGNORED.getCode(), caseStore.get(CASE_ID).getStatus());
        assertEquals(TalentStatusEnum.ACTIVE.getCode(), profileStore.get(MERGED_ID).getTalentStatus());
        assertNull(profileStore.get(MERGED_ID).getMergedToId());

        // 未填写忽略原因必须被拒绝
        DuplicateIgnoreBo blank = new DuplicateIgnoreBo();
        ServiceException ex = assertThrows(ServiceException.class, () -> service.ignore(CASE_ID, blank));
        assertEquals("忽略原因不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("不允许在合并中选择白名单之外的字段")
    void shouldRejectNonWhitelistedFieldDecision() {
        prepareMergeableCase();
        TalentMergeBo bo = mergeBo(null);
        bo.setFieldDecisions(List.of(decision("phoneCipher", "merged")));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.merge(CASE_ID, bo));

        assertTrue(ex.getMessage().contains("不允许在合并中选择该字段的取值"));
        assertTrue(mergeLogs.isEmpty(), "非法入参不得产生合并快照");
        assertTrue(caseStore.get(CASE_ID).getStatus().equals(DuplicateStatusEnum.PENDING.getCode()));
    }

    @Test
    @DisplayName("疑似重复列表：无可见人才时直接返回空页")
    void shouldReturnEmptyPageWhenNoVisibleTalent() {
        prepareMergeableCase();
        service.visibleTalentIds = List.of();

        PageResult<TalentDuplicateCaseVo> result = service.queryPage(new TalentDuplicateQueryBo(), null);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRows().isEmpty());
    }

    /* ------------------------------------------------------------------ 测试脚手架 ------------------------------------------------------------------ */

    /**
     * 构造一条可合并的疑似重复案件与两侧主档。
     */
    private void prepareMergeableCase() {
        TalentProfile keep = profile(KEEP_ID, "TAL1001", "张三", "新公司", "Java 工程师", 3);
        keep.setCurrentCity("北京");
        keep.setHighestEducation(null);
        profileStore.put(KEEP_ID, keep);

        TalentProfile merged = profile(MERGED_ID, "TAL1002", "张三丰", "旧公司", "高级 Java 工程师", 3);
        merged.setCurrentCity("上海");
        merged.setHighestEducation("本科");
        profileStore.put(MERGED_ID, merged);

        TalentDuplicateCase duplicateCase = new TalentDuplicateCase();
        duplicateCase.setCaseId(CASE_ID);
        duplicateCase.setSourceTalentId(KEEP_ID);
        duplicateCase.setTargetTalentId(MERGED_ID);
        duplicateCase.setMatchLevel("strong");
        duplicateCase.setMatchReason("电话命中");
        duplicateCase.setStatus(DuplicateStatusEnum.PENDING.getCode());
        caseStore.put(CASE_ID, duplicateCase);
    }

    /**
     * 构造最小可用的人才主档。
     *
     * @param talentId         主档ID
     * @param talentNo         人才编号
     * @param name             姓名
     * @param currentCompany   当前公司
     * @param expectedPosition 期望岗位
     * @param version          乐观锁版本
     * @return 人才主档
     */
    private TalentProfile profile(Long talentId, String talentNo, String name,
                                  String currentCompany, String expectedPosition, Integer version) {
        TalentProfile profile = new TalentProfile();
        profile.setTalentId(talentId);
        profile.setTalentNo(talentNo);
        profile.setName(name);
        profile.setCurrentCompany(currentCompany);
        profile.setExpectedPosition(expectedPosition);
        profile.setTalentStatus(TalentStatusEnum.ACTIVE.getCode());
        // 集团共享：集团级管理员可见，资源级鉴权通过
        profile.setVisibilityType(TalentVisibilityTypeEnum.GROUP.getCode());
        profile.setDelFlag("0");
        profile.setVersion(version);
        return profile;
    }

    /**
     * 构造合并入参。
     *
     * @param mergedVersion 被合并主档版本号，null 表示与主档一致
     * @return 合并入参
     */
    private TalentMergeBo mergeBo(Integer mergedVersion) {
        TalentMergeBo bo = new TalentMergeBo();
        bo.setKeepTalentId(KEEP_ID);
        bo.setKeepVersion(3);
        bo.setMergedTalentId(MERGED_ID);
        bo.setMergedVersion(mergedVersion == null ? 3 : mergedVersion);
        bo.setMergeReason("疑似同一人（电话哈希命中）");
        return bo;
    }

    /**
     * 构造冲突字段决策。
     *
     * @param field 字段名
     * @param from  取值来源
     * @return 字段决策
     */
    private TalentMergeBo.FieldDecision decision(String field, String from) {
        TalentMergeBo.FieldDecision decision = new TalentMergeBo.FieldDecision();
        decision.setField(field);
        decision.setFrom(from);
        return decision;
    }

    /* ------------------------------------------------------------------ 替身构造 ------------------------------------------------------------------ */

    /**
     * 构造疑似重复案件 Mapper 替身。
     *
     * @return Mapper 替身
     */
    private TalentDuplicateCaseMapper caseMapperStub() {
        return (TalentDuplicateCaseMapper) Proxy.newProxyInstance(
            TalentDuplicateCaseMapper.class.getClassLoader(),
            new Class<?>[]{TalentDuplicateCaseMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> caseStore.get((Long) args[0]);
                case "insert" -> {
                    TalentDuplicateCase entity = (TalentDuplicateCase) args[0];
                    if (entity.getCaseId() == null) {
                        entity.setCaseId(CASE_ID + 100);
                    }
                    caseStore.put(entity.getCaseId(), entity);
                    yield 1;
                }
                case "updateById" -> {
                    TalentDuplicateCase entity = (TalentDuplicateCase) args[0];
                    caseStore.put(entity.getCaseId(), entity);
                    yield 1;
                }
                case "closePendingCases" -> {
                    // 合并收尾：把两主档之间的「未终结」案件（pending / confirmed）置为已合并
                    int closed = 0;
                    Long keepTalentId = (Long) args[0];
                    Long mergedTalentId = (Long) args[1];
                    for (TalentDuplicateCase item : caseStore.values()) {
                        boolean pairMatched = (item.getSourceTalentId().equals(mergedTalentId)
                            && item.getTargetTalentId().equals(keepTalentId))
                            || (item.getSourceTalentId().equals(keepTalentId)
                            && item.getTargetTalentId().equals(mergedTalentId));
                        boolean openStatus = DuplicateStatusEnum.PENDING.getCode().equals(item.getStatus())
                            || DuplicateStatusEnum.CONFIRMED.getCode().equals(item.getStatus());
                        if (pairMatched && openStatus) {
                            item.setStatus(DuplicateStatusEnum.MERGED.getCode());
                            item.setHandledBy((Long) args[2]);
                            item.setHandledTime(java.time.LocalDateTime.now());
                            closed++;
                        }
                    }
                    yield closed;
                }
                case "selectVoPage" -> emptyPage();
                case "toString" -> "TalentDuplicateCaseMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造合并日志 Mapper 替身。
     *
     * @return Mapper 替身
     */
    private TalentMergeLogMapper mergeLogMapperStub() {
        return (TalentMergeLogMapper) Proxy.newProxyInstance(
            TalentMergeLogMapper.class.getClassLoader(),
            new Class<?>[]{TalentMergeLogMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "insert" -> {
                    TalentMergeLog entity = (TalentMergeLog) args[0];
                    if (entity.getMergeId() == null) {
                        entity.setMergeId(3001L);
                    }
                    mergeLogs.add(entity);
                    yield 1;
                }
                case "selectVoPage" -> emptyPage();
                case "toString" -> "TalentMergeLogMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造人才主档 Mapper 替身（记录全部调用，供「无物理删除」断言使用）。
     *
     * @return Mapper 替身
     */
    private TalentProfileMapper profileMapperStub() {
        return (TalentProfileMapper) Proxy.newProxyInstance(
            TalentProfileMapper.class.getClassLoader(),
            new Class<?>[]{TalentProfileMapper.class},
            (proxy, method, args) -> {
                profileMapperCalls.add(method.getName());
                return switch (method.getName()) {
                    case "selectById" -> profileStore.get((Long) args[0]);
                    case "updateById" -> {
                        TalentProfile entity = (TalentProfile) args[0];
                        // 被合并主档可被要求返回 0 行，用于构造乐观锁冲突；
                        // 冲突时不得把已改状态/已补 merged_to_id 的实体写回内存库
                        boolean targetMerged = entity.getTalentId().equals(MERGED_ID);
                        boolean conflict = targetMerged && !mergedUpdateRows.isEmpty()
                            && mergedUpdateRows.remove(0) == 0;
                        if (targetMerged) {
                            updateByIdCallsForMerged++;
                        }
                        if (!conflict) {
                            profileStore.put(entity.getTalentId(), entity);
                        }
                        yield conflict ? 0 : 1;
                    }
                    case "selectVisibleTalentIds" -> service.visibleTalentIds;
                    case "selectObjs" -> List.of();
                    case "toString" -> "TalentProfileMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                };
            });
    }

    /**
     * 构造关系迁移替身（只实现 {@code snapshot}/{@code migrate}，其余能力一概不提供，
     * 以便断言服务未调用任何删除类方法）。
     *
     * @return 迁移替身
     */
    private ITalentMergeRelationMigrator migratorStub() {
        return new ITalentMergeRelationMigrator() {

            @Override
            public TalentMergeRelationSnapshot snapshot(Long talentId) {
                migratorCalls.add("snapshot");
                if (migratorCalls.stream().noneMatch("migrate"::equals)) {
                    return new TalentMergeRelationSnapshot(TalentMergeRelationSnapshot.Kind.BEFORE, beforeCounts);
                }
                Map<String, Long> after = new LinkedHashMap<>();
                beforeCounts.keySet().forEach(key -> after.put(key, 0L));
                return new TalentMergeRelationSnapshot(TalentMergeRelationSnapshot.Kind.AFTER, after);
            }

            @Override
            public Map<String, Long> migrate(Long keepTalentId, Long mergedTalentId, Long operatorId) {
                migratorCalls.add("migrate");
                assertNotNull(keepTalentId, "关系迁移必须携带保留主档ID");
                assertNotNull(mergedTalentId, "关系迁移必须携带被合并主档ID");
                // 关系表用自定义 SQL 改归属，不会触发 MyBatis-Plus 审计自动填充：
                // 必须显式收到操作人，才能回写 update_by/update_time
                assertEquals(9L, operatorId, "关系迁移必须携带操作人（用于回写 update_by）");
                return new LinkedHashMap<>(beforeCounts);
            }

            @Override
            public String toString() {
                return "TalentMergeRelationMigratorStub";
            }
        };
    }

    /**
     * 构造审计记录器替身。
     *
     * <p>{@link SensitiveAuditRecorder} 是具体类（非接口），无法用 JDK 动态代理直接代理，
     * 因此这里代理它内部的 {@code RecruitSensitiveAuditMapper#insert}，
     * 由真实的 {@code record(...)} 方法完成装配后再把落库实体回收到断言列表。</p>
     *
     * @return 审计替身
     */
    private SensitiveAuditRecorder auditRecorderStub() {
        RecruitSensitiveAuditMapper mapperStub = (RecruitSensitiveAuditMapper) Proxy.newProxyInstance(
            RecruitSensitiveAuditMapper.class.getClassLoader(),
            new Class<?>[]{RecruitSensitiveAuditMapper.class},
            (proxy, method, args) -> {
                if ("insert".equals(method.getName()) && args != null && args.length == 1
                    && args[0] instanceof RecruitSensitiveAudit audit) {
                    audits.add(new AuditRow(audit.getEventType(), audit.getBizType(), audit.getBizId(),
                        audit.getPurpose(), audit.getResult(), audit.getDetailJson(), audit.getOperatorId()));
                    return 1;
                }
                return switch (method.getName()) {
                    case "toString" -> "RecruitSensitiveAuditMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                };
            });
        return new SensitiveAuditRecorder(mapperStub);
    }

    /**
     * 构造事件发布器替身（记录事件）。
     *
     * @return 事件发布器替身
     */
    private ApplicationEventPublisher eventPublisherStub() {
        return (ApplicationEventPublisher) Proxy.newProxyInstance(
            ApplicationEventPublisher.class.getClassLoader(),
            new Class<?>[]{ApplicationEventPublisher.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "publishEvent" -> {
                    publishedEvents.add(args[0]);
                    yield null;
                }
                case "toString" -> "ApplicationEventPublisherStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造空分页结果。
     *
     * @param <T> 记录类型
     * @return 空分页
     */
    @SuppressWarnings("unchecked")
    private <T> IPage<T> emptyPage() {
        return (IPage<T>) new Page<>(1, 10, 0);
    }

    /**
     * 取返回类型的默认值（动态代理必须为未实现方法返回合法值）。
     *
     * @param type 返回类型
     * @return 默认值
     */
    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(type)) {
            return false;
        }
        if (char.class.equals(type)) {
            return (char) 0;
        }
        if (long.class.equals(type)) {
            return 0L;
        }
        if (short.class.equals(type)) {
            return (short) 0;
        }
        if (byte.class.equals(type)) {
            return (byte) 0;
        }
        if (double.class.equals(type)) {
            return 0D;
        }
        if (float.class.equals(type)) {
            return 0F;
        }
        return 0;
    }

    /**
     * 审计行（仅测试使用）。
     *
     * @param eventType  事件类型
     * @param bizType    业务类型
     * @param bizId      业务对象ID
     * @param purpose    操作事由
     * @param result     操作结果
     * @param detailJson 明细
     * @param operatorId 操作人用户ID
     * @author hr-talent
     */
    private record AuditRow(String eventType, String bizType, Long bizId,
                            String purpose, String result, String detailJson, Long operatorId) {
    }

    /**
     * 可测试的服务子类：把「权限判定」「登录用户读取」与「JSON 序列化」替换为可控实现。
     *
     * @author hr-talent
     */
    private static final class TestableService extends TalentDuplicateServiceImpl {

        /**
         * 是否集团级管理员。
         */
        private boolean groupLevelAdmin = true;

        /**
         * 当前用户可见的人才ID集合。
         */
        private List<Long> visibleTalentIds = List.of(KEEP_ID, MERGED_ID);

        /**
         * 内存主档库（与测试共享，静态嵌套类不能直接访问外部实例字段）。
         */
        private final Map<Long, TalentProfile> profileStore;

        /**
         * 构造被测服务。
         *
         * @param caseMapper     案件 Mapper
         * @param mergeLogMapper 合并日志 Mapper
         * @param profileMapper  主档 Mapper
         * @param migrator       关系迁移替身
         * @param scopeService   可见范围领域服务
         * @param auditRecorder  审计记录器
         * @param eventPublisher 事件发布器
         * @param profileStore   内存主档库
         */
        private TestableService(TalentDuplicateCaseMapper caseMapper,
                                TalentMergeLogMapper mergeLogMapper,
                                TalentProfileMapper profileMapper,
                                ITalentMergeRelationMigrator migrator,
                                TalentScopeDomainService scopeService,
                                SensitiveAuditRecorder auditRecorder,
                                ApplicationEventPublisher eventPublisher,
                                Map<Long, TalentProfile> profileStore) {
            super(caseMapper, mergeLogMapper, profileMapper, migrator, scopeService, auditRecorder, eventPublisher);
            this.profileStore = profileStore;
        }

        @Override
        protected boolean isMergeAllowed() {
            return groupLevelAdmin;
        }

        @Override
        protected Long currentUserId() {
            return 9L;
        }

        @Override
        protected TalentProfile requireProfile(Long talentId) {
            // 无 Sa-Token 上下文：只从内存库取主档，跳过依赖登录会话的可见性鉴权
            // （可见性规则由 TalentScopeDomainServiceTest 单独覆盖）
            TalentProfile profile = profileStore.get(talentId);
            if (profile == null) {
                throw new ServiceException(TalentScopeDomainService.TALENT_NOT_FOUND);
            }
            return profile;
        }

        @Override
        protected List<Long> resolveVisibleTalentIds() {
            // 无 Sa-Token 上下文：可见人才集合由测试直接给定
            return visibleTalentIds;
        }

        @Override
        protected String toJson(Object value) {
            // 单测不引入 Spring 容器的 JsonMapper：用最小 JSON 拼装即可满足快照断言
            return value == null ? null : MiniJson.write(value);
        }
    }

    /**
     * 最小 JSON 拼装器（仅测试使用，避免依赖 Spring 容器提供的 {@code JsonMapper}）。
     *
     * @author hr-talent
     */
    private static final class MiniJson {

        /**
         * 工具类禁止实例化。
         */
        private MiniJson() {
        }

        /**
         * 把 Map/List/标量拼装为 JSON 文本。
         *
         * @param value 待序列化对象
         * @return JSON 文本
         */
        private static String write(Object value) {
            StringBuilder sb = new StringBuilder();
            append(sb, value);
            return sb.toString();
        }

        /**
         * 递归追加 JSON 片段。
         *
         * @param sb    输出缓冲
         * @param value 值
         */
        @SuppressWarnings("unchecked")
        private static void append(StringBuilder sb, Object value) {
            if (value == null) {
                sb.append("null");
            } else if (value instanceof Map<?, ?> map) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : ((Map<Object, Object>) map).entrySet()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    appendString(sb, String.valueOf(entry.getKey()));
                    sb.append(':');
                    append(sb, entry.getValue());
                }
                sb.append('}');
            } else if (value instanceof Iterable<?> iterable) {
                sb.append('[');
                boolean first = true;
                for (Object item : iterable) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    append(sb, item);
                }
                sb.append(']');
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                appendString(sb, String.valueOf(value));
            }
        }

        /**
         * 追加 JSON 字符串字面量。
         *
         * @param sb    输出缓冲
         * @param value 字符串
         */
        private static void appendString(StringBuilder sb, String value) {
            sb.append('"');
            for (char c : value.toCharArray()) {
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> sb.append(c);
                }
            }
            sb.append('"');
        }

    }

}
