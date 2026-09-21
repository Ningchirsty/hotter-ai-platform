package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitBackgroundBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitBackgroundQueryBo;
import org.dromara.hrtalent.domain.entity.RecruitBackground;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitBackgroundDetailVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitBackgroundVo;
import org.dromara.hrtalent.enums.BackgroundResultEnum;
import org.dromara.hrtalent.enums.BackgroundStatusEnum;
import org.dromara.hrtalent.event.BackgroundResultChangedEvent;
import org.dromara.hrtalent.mapper.RecruitBackgroundMapper;
import org.dromara.hrtalent.service.recruitment.IRecruitBackgroundService;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 招聘背调服务实现（SPEC-P3 §2.4、§3.4，设计文档 §7.4、§8.7、§15.1、§15.2）。
 *
 * <p><b>敏感字段独立权限</b>：</p>
 * <ul>
 *     <li>{@link #queryPage} / {@link #getDetail} 返回的 {@link RecruitBackgroundVo} 结构上不含
 *     {@code detail_cipher}，因此列表与普通详情不可能泄露明细；</li>
 *     <li>{@link #viewDetail} 是唯一的明文明细出口。它<b>先</b>调用
 *     {@link SensitiveAuditRecorder#record} 写 {@code background_view} 审计再返回；
 *     {@code purpose} 为空时记 {@code denied} 审计并抛中文提示（不允许无用途查看）；</li>
 *     <li>{@code detail_cipher} 由 {@code ruoyi-common-encrypt} 的 {@code @EncryptField} 密文落库，
 *     任何日志都不输出明细（设计文档 §21.9）。</li>
 * </ul>
 *
 * <p><b>与应聘域的边界</b>：本服务<b>不写</b> {@code hr_recruit_application}；「进入待录用前必须有
 * 背调结论或经授权的免背调原因」的校验由应聘服务负责。本服务只发布
 * {@link BackgroundResultChangedEvent}（<b>事务提交后</b>发布，与应聘域保持相同时序；
 * 消费者可据此刷新阶段或计划状态，也可改为轮询）。</p>
 *
 * <p><b>当前有效背调</b>（设计文档 §7.5）：同一应聘记录只保留一条当前有效背调。新建时会把该应聘记录下
 * 已有的有效背调置为 {@code cancelled}。因 {@code hr_recruit_background} 既没有 {@code current_flag}、
 * 也没有「被替代」状态编码，只能复用 {@code cancelled} 表达失效，该语义无法与「人工取消」区分——
 * 已作为 DDL 缺口上报主控，<b>本类不自行加列</b>。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitBackgroundServiceImpl implements IRecruitBackgroundService {

    /**
     * 未取得候选人授权。
     */
    private static final String AUTHORIZED_NO = "0";

    /**
     * 已取得候选人授权。
     */
    private static final String AUTHORIZED_YES = "1";

    /**
     * 中文（CJK 统一表意文字）匹配：未通过原因分类必须是字典编码，不得存中文。
     */
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");

    /**
     * 背调 Mapper。
     */
    private final RecruitBackgroundMapper recruitBackgroundMapper;

    /**
     * 敏感操作审计记录器（背调明细查看必须留痕）。
     */
    private final SensitiveAuditRecorder sensitiveAuditRecorder;

    /**
     * 领域事件发布器（结论变化后通知应聘阶段与计划状态服务）。
     */
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PageResult<RecruitBackgroundVo> queryPage(RecruitBackgroundQueryBo bo, PageQuery pageQuery) {
        RecruitBackgroundQueryBo query = bo == null ? new RecruitBackgroundQueryBo() : bo;
        LambdaQueryWrapper<RecruitBackground> wrapper = new LambdaQueryWrapper<RecruitBackground>()
            .eq(query.getApplicationId() != null, RecruitBackground::getApplicationId, query.getApplicationId())
            .eq(query.getCheckerId() != null, RecruitBackground::getCheckerId, query.getCheckerId())
            .eq(StringUtils.isNotBlank(query.getResult()), RecruitBackground::getResult, query.getResult())
            .eq(StringUtils.isNotBlank(query.getStatus()), RecruitBackground::getStatus, query.getStatus())
            .eq(StringUtils.isNotBlank(query.getAuthorizedFlag()), RecruitBackground::getAuthorizedFlag, query.getAuthorizedFlag())
            .eq(StringUtils.isNotBlank(query.getFailureReasonCode()), RecruitBackground::getFailureReasonCode, query.getFailureReasonCode())
            .ge(query.getCheckTimeBegin() != null, RecruitBackground::getCheckTime, query.getCheckTimeBegin())
            .le(query.getCheckTimeEnd() != null, RecruitBackground::getCheckTime, query.getCheckTimeEnd())
            .orderByDesc(RecruitBackground::getCreateTime);
        // VO 不含 detail_cipher，列表结构上不可能返回明细
        Page<RecruitBackgroundVo> page = recruitBackgroundMapper.selectVoPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public RecruitBackgroundVo getDetail(Long backgroundId) {
        return toVo(loadBackground(backgroundId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RecruitBackgroundBo bo) {
        if (bo == null || bo.getApplicationId() == null) {
            throw new ServiceException("应聘记录ID不能为空");
        }
        String authorizedFlag = resolveAuthorizedFlag(bo.getAuthorizedFlag(), AUTHORIZED_NO);
        String status = resolveCreateStatus(bo.getStatus());
        String result = resolveResult(bo.getResult());
        String failureReasonCode = normalizeFailureReasonCode(bo.getFailureReasonCode());
        String waiveReason = bo.getWaiveReason();
        validateResultRules(result, failureReasonCode, waiveReason);

        // 同一应聘记录只保留一条当前有效背调：先让旧记录失效（设计文档 §7.5）
        supersedeExisting(bo.getApplicationId());

        RecruitBackground entity = new RecruitBackground();
        entity.setApplicationId(bo.getApplicationId());
        entity.setAuthorizedFlag(authorizedFlag);
        entity.setAuthorizeTime(bo.getAuthorizeTime());
        entity.setCheckerId(bo.getCheckerId());
        entity.setCheckTime(bo.getCheckTime());
        entity.setCheckStartDate(bo.getCheckStartDate());
        entity.setCheckEndDate(bo.getCheckEndDate());
        entity.setResult(result);
        entity.setFailureReasonCode(failureReasonCode);
        entity.setCheckItems(bo.getCheckItems());
        entity.setDetailCipher(bo.getDetailCipher());
        entity.setWaiveReason(waiveReason);
        entity.setStatus(status);
        entity.setRemark(bo.getRemark());
        recruitBackgroundMapper.insert(entity);

        // 只记录非敏感上下文：不输出明细、不输出免背调原因正文
        log.info("新增背调记录, backgroundId={}, applicationId={}, status={}, result={}, authorized={}",
            entity.getBackgroundId(), entity.getApplicationId(), status, result, authorizedFlag);
        if (isConclusive(result)) {
            publishResultChanged(entity);
        }
        return entity.getBackgroundId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RecruitBackgroundBo bo) {
        if (bo == null || bo.getBackgroundId() == null) {
            throw new ServiceException("背调记录ID不能为空");
        }
        RecruitBackground exist = loadBackground(bo.getBackgroundId());
        if (BackgroundStatusEnum.CANCELLED.getCode().equals(exist.getStatus())) {
            throw new ServiceException("已取消或被替代的背调记录不可修改，请更新当前有效背调");
        }
        // 合并语义：入参为 null 表示不修改，先算出「生效值」再统一校验，避免局部更新绕过规则
        String effectiveAuthorizedFlag = bo.getAuthorizedFlag() == null
            ? exist.getAuthorizedFlag() : resolveAuthorizedFlag(bo.getAuthorizedFlag(), AUTHORIZED_NO);
        String effectiveStatus = StringUtils.isBlank(bo.getStatus())
            ? exist.getStatus() : resolveStatus(bo.getStatus()).getCode();
        String effectiveResult = resolveResult(StringUtils.isBlank(bo.getResult()) ? exist.getResult() : bo.getResult());
        String effectiveFailureCode = bo.getFailureReasonCode() == null
            ? exist.getFailureReasonCode() : normalizeFailureReasonCode(bo.getFailureReasonCode());
        String effectiveWaiveReason = bo.getWaiveReason() == null ? exist.getWaiveReason() : bo.getWaiveReason();
        // 结论不是「不通过」时先清空未通过原因：既避免留下与新结论矛盾的脏数据，
        // 也保证「不通过原因只属于不通过结论」的校验不会被历史值误伤
        if (!BackgroundResultEnum.FAIL.getCode().equals(effectiveResult)) {
            effectiveFailureCode = null;
        }
        validateResultRules(effectiveResult, effectiveFailureCode, effectiveWaiveReason);

        RecruitBackground update = new RecruitBackground();
        // 所属应聘记录不可修改：不写入 application_id
        update.setAuthorizedFlag(effectiveAuthorizedFlag);
        update.setAuthorizeTime(bo.getAuthorizeTime() == null ? exist.getAuthorizeTime() : bo.getAuthorizeTime());
        update.setCheckerId(bo.getCheckerId() == null ? exist.getCheckerId() : bo.getCheckerId());
        update.setCheckTime(bo.getCheckTime() == null ? exist.getCheckTime() : bo.getCheckTime());
        update.setCheckStartDate(bo.getCheckStartDate() == null ? exist.getCheckStartDate() : bo.getCheckStartDate());
        update.setCheckEndDate(bo.getCheckEndDate() == null ? exist.getCheckEndDate() : bo.getCheckEndDate());
        update.setResult(effectiveResult);
        update.setFailureReasonCode(effectiveFailureCode);
        update.setCheckItems(bo.getCheckItems() == null ? exist.getCheckItems() : bo.getCheckItems());
        update.setDetailCipher(bo.getDetailCipher() == null ? exist.getDetailCipher() : bo.getDetailCipher());
        update.setWaiveReason(effectiveWaiveReason);
        update.setStatus(effectiveStatus);
        update.setRemark(bo.getRemark() == null ? exist.getRemark() : bo.getRemark());
        LambdaUpdateWrapper<RecruitBackground> wrapper = new LambdaUpdateWrapper<RecruitBackground>()
            .eq(RecruitBackground::getBackgroundId, exist.getBackgroundId());
        if (effectiveFailureCode == null) {
            // 未通过原因被清空时必须显式 set null（实体更新默认忽略 null 字段）
            wrapper.set(RecruitBackground::getFailureReasonCode, null);
        }
        int rows = recruitBackgroundMapper.update(update, wrapper);
        if (rows == 0) {
            throw new ServiceException("背调记录不存在或已删除");
        }
        log.info("更新背调记录, backgroundId={}, status={}, result={}",
            exist.getBackgroundId(), effectiveStatus, effectiveResult);
        // 结论发生变化且已回执时发布事件（事件只承载标识，不含明细）
        if (isConclusive(effectiveResult) && !effectiveResult.equals(exist.getResult())) {
            RecruitBackground changed = new RecruitBackground();
            changed.setBackgroundId(exist.getBackgroundId());
            changed.setApplicationId(exist.getApplicationId());
            changed.setResult(effectiveResult);
            changed.setStatus(effectiveStatus);
            changed.setCheckerId(update.getCheckerId());
            publishResultChanged(changed);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>本方法<b>刻意不加事务</b>：审计写入绝不能因为后续返回组装失败而被回滚，
     * 且「先审计、后返回明细」的次序必须在任何情况下成立。</p>
     */
    @Override
    public RecruitBackgroundDetailVo viewDetail(Long backgroundId, String purpose) {
        if (backgroundId == null) {
            throw new ServiceException("背调记录ID不能为空");
        }
        if (StringUtils.isBlank(purpose)) {
            // 无用途查看必须留痕：先记 denied 审计再拒绝
            sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_BACKGROUND_VIEW,
                SensitiveAuditRecorder.BIZ_BACKGROUND, backgroundId, null, SensitiveAuditRecorder.RESULT_DENIED);
            throw new ServiceException("查看背调明细必须填写用途（purpose）");
        }
        RecruitBackground entity = recruitBackgroundMapper.selectById(backgroundId);
        if (entity == null) {
            sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_BACKGROUND_VIEW,
                SensitiveAuditRecorder.BIZ_BACKGROUND, backgroundId, purpose, SensitiveAuditRecorder.RESULT_DENIED);
            throw new ServiceException("背调记录不存在或已删除");
        }
        // 先写审计（含用途），再返回明细；审计记录器不写入任何明细明文
        sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_BACKGROUND_VIEW,
            SensitiveAuditRecorder.BIZ_BACKGROUND, backgroundId, purpose, SensitiveAuditRecorder.RESULT_SUCCESS);
        log.info("查看背调明细, backgroundId={}", backgroundId);
        return toDetailVo(entity);
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 让同一应聘记录下已有的有效背调失效（设计文档 §7.5）。
     *
     * <p>先在数据库侧按 {@code application_id} 收窄，再在内存中跳过已失效记录：
     * 内存判断是「当前有效背调唯一」不变式的权威守卫，也让该规则可脱离数据库单测。</p>
     *
     * @param applicationId 应聘记录ID
     */
    private void supersedeExisting(Long applicationId) {
        List<RecruitBackground> existing = recruitBackgroundMapper.selectList(
            new LambdaQueryWrapper<RecruitBackground>().eq(RecruitBackground::getApplicationId, applicationId));
        if (existing == null || existing.isEmpty()) {
            return;
        }
        for (RecruitBackground old : existing) {
            if (old == null || old.getBackgroundId() == null) {
                continue;
            }
            if (BackgroundStatusEnum.CANCELLED.getCode().equals(old.getStatus())) {
                continue;
            }
            old.setStatus(BackgroundStatusEnum.CANCELLED.getCode());
            recruitBackgroundMapper.updateById(old);
            log.info("旧背调记录因新建背调而失效, backgroundId={}, applicationId={}",
                old.getBackgroundId(), applicationId);
        }
    }

    /**
     * 加载背调记录，不存在时抛中文提示。
     *
     * @param backgroundId 背调记录ID
     * @return 背调实体
     */
    private RecruitBackground loadBackground(Long backgroundId) {
        if (backgroundId == null) {
            throw new ServiceException("背调记录ID不能为空");
        }
        RecruitBackground entity = recruitBackgroundMapper.selectById(backgroundId);
        if (entity == null) {
            throw new ServiceException("背调记录不存在或已删除");
        }
        return entity;
    }

    /**
     * 解析是否已获得授权标志，空值回落为「否」。
     *
     * @param authorizedFlag 入参标志
     * @param defaultValue   空值默认值
     * @return 规范化标志
     */
    private String resolveAuthorizedFlag(String authorizedFlag, String defaultValue) {
        if (StringUtils.isBlank(authorizedFlag)) {
            return defaultValue;
        }
        if (!AUTHORIZED_NO.equals(authorizedFlag) && !AUTHORIZED_YES.equals(authorizedFlag)) {
            throw new ServiceException("是否已获得候选人授权只能为 0 或 1");
        }
        return authorizedFlag;
    }

    /**
     * 解析新增时的背调状态：空值按草稿，禁止新建即已取消。
     *
     * @param status 入参状态编码
     * @return 规范化状态编码
     */
    private String resolveCreateStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return BackgroundStatusEnum.DRAFT.getCode();
        }
        BackgroundStatusEnum target = resolveStatus(status);
        if (BackgroundStatusEnum.CANCELLED == target) {
            throw new ServiceException("新建背调记录状态不能为已取消");
        }
        return target.getCode();
    }

    /**
     * 解析背调状态编码，非法编码一律拒绝（fail-safe）。
     *
     * @param status 状态编码
     * @return 状态枚举
     */
    private BackgroundStatusEnum resolveStatus(String status) {
        BackgroundStatusEnum target = BackgroundStatusEnum.find(status);
        if (target == null) {
            throw new ServiceException("未知的背调状态：" + status
                + "（可选 draft/checking/finished/cancelled）");
        }
        return target;
    }

    /**
     * 解析背调结论编码，空值按「待背调」。
     *
     * @param result 结论编码
     * @return 规范化结论编码
     */
    private String resolveResult(String result) {
        if (StringUtils.isBlank(result)) {
            return BackgroundResultEnum.PENDING.getCode();
        }
        BackgroundResultEnum target = BackgroundResultEnum.find(result);
        if (target == null) {
            throw new ServiceException("背调结论不合法，请使用字典 recruit_background_result 的编码"
                + "（pending/pass/fail/waived）");
        }
        return target.getCode();
    }

    /**
     * 规范化未通过原因编码：必须是字典编码，不得包含中文。
     *
     * @param failureReasonCode 原因编码
     * @return 去除首尾空白后的编码；空值返回 null
     */
    private String normalizeFailureReasonCode(String failureReasonCode) {
        if (StringUtils.isBlank(failureReasonCode)) {
            return null;
        }
        String code = failureReasonCode.trim();
        if (CHINESE_PATTERN.matcher(code).find()) {
            throw new ServiceException("未通过原因分类必须使用字典编码，不能填写中文");
        }
        return code;
    }

    /**
     * 校验结论相关硬规则（设计文档 §7.2 第 4 条、§8.7）。
     *
     * @param result            结论编码
     * @param failureReasonCode 未通过原因编码
     * @param waiveReason       免背调原因
     */
    private void validateResultRules(String result, String failureReasonCode, String waiveReason) {
        if (BackgroundResultEnum.WAIVED.getCode().equals(result) && StringUtils.isBlank(waiveReason)) {
            throw new ServiceException("背调结论为已豁免（waived）时必须填写免背调原因");
        }
        if (BackgroundResultEnum.FAIL.getCode().equals(result) && StringUtils.isBlank(failureReasonCode)) {
            throw new ServiceException("背调结论为不通过（fail）时必须填写未通过原因分类");
        }
        if (!BackgroundResultEnum.FAIL.getCode().equals(result) && StringUtils.isNotBlank(failureReasonCode)) {
            throw new ServiceException("仅背调不通过（fail）时可填写未通过原因分类");
        }
    }

    /**
     * 判断结论是否已回执（可驱动后续阶段与状态刷新）。
     *
     * @param result 结论编码
     * @return 通过/不通过/已豁免返回 true
     */
    private boolean isConclusive(String result) {
        return BackgroundResultEnum.PASS.getCode().equals(result)
            || BackgroundResultEnum.FAIL.getCode().equals(result)
            || BackgroundResultEnum.WAIVED.getCode().equals(result);
    }

    /**
     * 发布背调结论变化事件（<b>事务提交后</b>发布，设计文档 §21.6）。
     *
     * <p>与应聘域（{@code RecruitApplicationServiceImpl#publishAfterCommit}）保持相同时序：
     * 涉及数据库一致性的监听器不得在事务提交前被触发，避免消费者读到未提交数据；
     * 无事务上下文时立即发布。</p>
     *
     * @param entity 背调实体（至少包含主键、应聘记录ID、结论与状态）
     */
    private void publishResultChanged(RecruitBackground entity) {
        BackgroundResultChangedEvent event = new BackgroundResultChangedEvent(
            entity.getBackgroundId(),
            entity.getApplicationId(),
            entity.getResult(),
            entity.getStatus(),
            entity.getCheckerId(),
            currentUserId());
        publishAfterCommit(event);
    }

    /**
     * 事务提交后发布领域事件（设计文档 §21.6）。
     *
     * <p>事件只负责后续派生刷新（应聘阶段校验、计划任务状态刷新），
     * 必须原子完成的写操作不交给事件。</p>
     *
     * @param event 领域事件
     */
    private void publishAfterCommit(Object event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(event);
                }
            });
        } else {
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 取当前登录用户ID；无登录态（如定时任务、单测）时返回 null。
     *
     * @return 用户ID或 null
     */
    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 实体转普通视图（<b>不含</b>背调明细）。
     *
     * @param entity 背调实体
     * @return 普通视图
     */
    private RecruitBackgroundVo toVo(RecruitBackground entity) {
        RecruitBackgroundVo vo = new RecruitBackgroundVo();
        vo.setBackgroundId(entity.getBackgroundId());
        vo.setApplicationId(entity.getApplicationId());
        vo.setAuthorizedFlag(entity.getAuthorizedFlag());
        vo.setAuthorizeTime(entity.getAuthorizeTime());
        vo.setCheckerId(entity.getCheckerId());
        vo.setCheckTime(entity.getCheckTime());
        vo.setCheckStartDate(entity.getCheckStartDate());
        vo.setCheckEndDate(entity.getCheckEndDate());
        vo.setResult(entity.getResult());
        vo.setFailureReasonCode(entity.getFailureReasonCode());
        vo.setCheckItems(entity.getCheckItems());
        vo.setWaiveReason(entity.getWaiveReason());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 实体转敏感明细视图（含明文明细，仅在写审计后调用）。
     *
     * @param entity 背调实体
     * @return 明细视图
     */
    private RecruitBackgroundDetailVo toDetailVo(RecruitBackground entity) {
        RecruitBackgroundDetailVo vo = new RecruitBackgroundDetailVo();
        vo.setBackgroundId(entity.getBackgroundId());
        vo.setApplicationId(entity.getApplicationId());
        vo.setAuthorizedFlag(entity.getAuthorizedFlag());
        vo.setAuthorizeTime(entity.getAuthorizeTime());
        vo.setCheckerId(entity.getCheckerId());
        vo.setCheckTime(entity.getCheckTime());
        vo.setCheckStartDate(entity.getCheckStartDate());
        vo.setCheckEndDate(entity.getCheckEndDate());
        vo.setResult(entity.getResult());
        vo.setFailureReasonCode(entity.getFailureReasonCode());
        vo.setCheckItems(entity.getCheckItems());
        vo.setDetailCipher(entity.getDetailCipher());
        vo.setWaiveReason(entity.getWaiveReason());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

}
