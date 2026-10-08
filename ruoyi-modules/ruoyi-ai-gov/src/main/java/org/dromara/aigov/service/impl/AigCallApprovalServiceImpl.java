package org.dromara.aigov.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.config.AigApprovalProperties;
import org.dromara.aigov.domain.AigCallApproval;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.aigov.domain.bo.AigCallApprovalBo;
import org.dromara.aigov.domain.bo.AigCallApprovalDecideBo;
import org.dromara.aigov.domain.vo.AigCallApprovalSweepVo;
import org.dromara.aigov.domain.vo.AigCallApprovalVo;
import org.dromara.aigov.enums.AigApprovalStatusEnum;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.mapper.AigCallApprovalMapper;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.mapper.AigRoutePolicyMapper;
import org.dromara.aigov.service.IAigCallApprovalService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 调用授权审批实现（C3：把 {@code aig_route_policy.require_approval} 做实）。
 *
 * <h3>为什么要有它</h3>
 * <p>路由策略上那个「调用前是否需要审批」的开关此前<b>没有任何效果</b>——全仓库唯一读它的
 * 地方是把它拼进 {@code policyHits} 的说明文本。打开开关、页面显示「需要」，调用照样跑。
 * 本类提供那个开关真正生效所需的「授权事实」。</p>
 *
 * <h3>六处刻意做对的地方</h3>
 * <ol>
 *     <li><b>授权粒度是人 × 能力 × 数据等级</b>：审批本就是按 (能力,数据等级) 的策略触发的。
 *         粒度取宽（只按能力）会让一次 INTERNAL 的审批变成一张覆盖 STRICT 的通行证，
 *         而数据等级是安全维度、越高越严。</li>
 *     <li><b>两个时间不能混</b>：{@code expire_time} 管「还能不能批准」，
 *         {@code valid_until} 管「授权还管不管用」。合成一个字段就必然有一边错——
 *         比如批准之后又拿"审批时限"去判断授权是否有效。</li>
 *     <li><b>授权过期不改状态</b>：过期是时间的函数（{@code valid_until > now}），不是需要落库的
 *         事件。若在过期时把 APPROVED 改成别的状态，「这个人曾经被批准过」就查不到了。</li>
 *     <li><b>申请人不得自审</b>：服务层强制。靠页面藏按钮不算约束——接口是公开的。</li>
 *     <li><b>决定与撤回都走条件更新（CAS）</b>：{@code update ... where status='PENDING'}，
 *         影响 0 行即说明有并发方先处理了。两个审批人同时点「批准」不会都成功，
 *         也不会把一张已驳回的单子覆盖成批准。</li>
 *     <li><b>申请前先核对策略</b>：不该被审批放行的情形（能力不存在、压根没策略、
 *         策略并不要求审批）当场说清楚——否则申请人提交完、批完、发现调用还是被拒，
 *         而真正的原因（没有路由策略）从头到尾没露过面。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigCallApprovalServiceImpl implements IAigCallApprovalService {

    /**
     * 记录状态：正常（参与判定）
     */
    private static final String STATUS_NORMAL = "0";

    /**
     * 策略要求审批（{@code aig_route_policy.require_approval}）
     */
    private static final String REQUIRE_APPROVAL = "Y";

    /**
     * 申请理由/审批意见的列宽
     */
    private static final int REMARK_MAX = 500;

    private final AigCallApprovalMapper approvalMapper;

    private final AigCapabilityMapper capabilityMapper;

    private final AigRoutePolicyMapper routePolicyMapper;

    private final AigApprovalProperties properties;

    @Override
    public PageResult<AigCallApprovalVo> queryPage(AigCallApprovalBo bo, PageQuery pageQuery) {
        AigCallApprovalBo query = bo == null ? new AigCallApprovalBo() : bo;
        LambdaQueryWrapper<AigCallApproval> wrapper = new LambdaQueryWrapper<AigCallApproval>()
            .eq(query.getRequesterId() != null, AigCallApproval::getRequesterId, query.getRequesterId())
            .eq(query.getApproverId() != null, AigCallApproval::getApproverId, query.getApproverId())
            .eq(StringUtils.isNotBlank(query.getCapabilityCode()),
                AigCallApproval::getCapabilityCode, query.getCapabilityCode())
            .eq(StringUtils.isNotBlank(query.getDataLevel()),
                AigCallApproval::getDataLevel, query.getDataLevel())
            .eq(StringUtils.isNotBlank(query.getStatus()), AigCallApproval::getStatus, query.getStatus())
            // 新的在前：审批人看的是「刚提交的」，申请人看的是「我刚交的」
            .orderByDesc(AigCallApproval::getCreateTime)
            .orderByDesc(AigCallApproval::getApprovalId);
        // 注意类型：不能把 selectVoPage 内联进 PageResult.build(...)——返回类型是自由类型变量，
        // 内联会让编译器按 Collection 反推 → 运行期 ClassCastException（空结果时表现为
        // 「cannot find converter from AigCallApproval to AigCallApprovalVo」）
        Page<AigCallApprovalVo> voPage = approvalMapper.selectVoPage(pageQuery.build(), wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long apply(Long requesterId, String requesterName, AigCallApprovalBo bo) {
        if (requesterId == null) {
            throw new ServiceException("申请人不能为空：无法确定这张申请单是谁提交的");
        }
        if (bo == null || StringUtils.isBlank(bo.getCapabilityCode())
            || StringUtils.isBlank(bo.getDataLevel())) {
            throw new ServiceException("能力编码与数据等级不能为空");
        }
        String capabilityCode = bo.getCapabilityCode().trim();
        AigDataLevelEnum dataLevel = AigDataLevelEnum.find(bo.getDataLevel());
        if (dataLevel == null) {
            throw new ServiceException("非法的数据等级：" + bo.getDataLevel());
        }

        // ① 能力必须存在且启用：为一个不存在的能力申请审批，批了也调不通
        AigCapability capability = capabilityMapper.selectOne(new LambdaQueryWrapper<AigCapability>()
            .eq(AigCapability::getCapabilityCode, capabilityCode));
        if (capability == null || !STATUS_NORMAL.equals(capability.getStatus())) {
            throw new ServiceException("能力不存在或已停用，无法为其申请调用授权：" + capabilityCode);
        }

        // ② 该组合必须有启用中的路由策略，且策略要求审批。
        //    这两种情况「审批放行不了」，必须现在说清楚：
        //    · 没有策略 → 调用会因「未配置策略」被直接拒绝，批准一张单子不会改变它；
        //    · 策略不要求审批 → 本来就能调，申请与批准都是空转。
        AigRoutePolicy policy = routePolicyMapper.selectOne(new LambdaQueryWrapper<AigRoutePolicy>()
            .eq(AigRoutePolicy::getCapabilityCode, capabilityCode)
            .eq(AigRoutePolicy::getDataLevel, dataLevel.getCode())
            .eq(AigRoutePolicy::getStatus, STATUS_NORMAL));
        if (policy == null) {
            throw new ServiceException("能力×数据等级没有启用中的路由策略，审批放行不了这种拒绝："
                + "调用会被判为「未配置该数据等级的路由策略」。请先配置路由策略（"
                + capabilityCode + " × " + dataLevel.getCode() + "）");
        }
        if (!REQUIRE_APPROVAL.equalsIgnoreCase(policy.getRequireApproval())) {
            throw new ServiceException("该能力×数据等级不要求调用审批（require_approval=N），"
                + "直接调用即可，无需申请：" + capabilityCode + " × " + dataLevel.getCode());
        }

        // ③ 同一个人 × 同一能力 × 同一等级，不重复挂待审批单
        Long pending = approvalMapper.selectCount(new LambdaQueryWrapper<AigCallApproval>()
            .eq(AigCallApproval::getRequesterId, requesterId)
            .eq(AigCallApproval::getCapabilityCode, capabilityCode)
            .eq(AigCallApproval::getDataLevel, dataLevel.getCode())
            .eq(AigCallApproval::getStatus, AigApprovalStatusEnum.PENDING.getCode()));
        if (pending != null && pending > 0L) {
            throw new ServiceException("已有一张待审批的申请单（同一个人 × 同一能力 × 同一数据等级）："
                + "请等它出结论，或先撤回再重新提交");
        }

        LocalDateTime now = LocalDateTime.now();
        AigCallApproval entity = new AigCallApproval();
        entity.setRequesterId(requesterId);
        entity.setRequesterName(requesterName);
        entity.setCapabilityCode(capabilityCode);
        entity.setDataLevel(dataLevel.getCode());
        entity.setReason(StringUtils.substring(bo.getReason(), 0, REMARK_MAX));
        entity.setStatus(AigApprovalStatusEnum.PENDING.getCode());
        entity.setExpireTime(now.plusMinutes(properties.getRequestTtlMinutes()));
        approvalMapper.insert(entity);
        log.info("提交调用授权申请, approvalId={}, requesterId={}, capability={}, dataLevel={}, 审批时限={}",
            entity.getApprovalId(), requesterId, capabilityCode, dataLevel.getCode(), entity.getExpireTime());
        return entity.getApprovalId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decide(Long approvalId, Long approverId, String approverName, AigCallApprovalDecideBo bo) {
        if (approvalId == null) {
            throw new ServiceException("审批单ID不能为空");
        }
        if (approverId == null) {
            throw new ServiceException("审批人不能为空：无法确定是谁做的这个决定");
        }
        if (bo == null || bo.getApproved() == null) {
            throw new ServiceException("请明确是批准还是驳回");
        }
        AigCallApproval row = approvalMapper.selectById(approvalId);
        if (row == null) {
            throw new ServiceException("审批单不存在：" + approvalId);
        }
        AigApprovalStatusEnum status = AigApprovalStatusEnum.find(row.getStatus());
        if (status != AigApprovalStatusEnum.PENDING) {
            throw new ServiceException("该申请单不是「待审批」，不能再处理：当前状态="
                + (status == null ? row.getStatus() : status.getDesc()) + "（approvalId=" + approvalId + "）");
        }

        LocalDateTime now = LocalDateTime.now();
        // 超时：超过审批时限就不能再批准。顺手把状态落成 EXPIRED，让「为什么批不了」
        // 与列表上看到的状态一致（否则列表还显示「待审批」，人会以为是权限问题）
        if (row.getExpireTime() != null && !now.isBefore(row.getExpireTime())) {
            AigCallApproval expired = new AigCallApproval();
            expired.setStatus(AigApprovalStatusEnum.EXPIRED.getCode());
            approvalMapper.update(expired, new LambdaUpdateWrapper<AigCallApproval>()
                .eq(AigCallApproval::getApprovalId, approvalId)
                .eq(AigCallApproval::getStatus, AigApprovalStatusEnum.PENDING.getCode()));
            throw new ServiceException("该申请单已超过审批时限（" + row.getExpireTime()
                + "），已置为「已超时」：一张放了几天的单子，此刻批准批的是历史而不是当下。"
                + "请申请人重新提交");
        }

        // 分离职责：申请人不得自审。这条不靠页面藏按钮——接口是公开的
        if (row.getRequesterId() != null && row.getRequesterId().equals(approverId)) {
            throw new ServiceException("不能审批自己提交的申请（分离职责）：请由其他人审批"
                + "（approvalId=" + approvalId + "）");
        }

        boolean approved = Boolean.TRUE.equals(bo.getApproved());
        if (!approved && StringUtils.isBlank(bo.getRemark())) {
            throw new ServiceException("驳回必须写明原因：申请人要据此修改后重新提交，"
                + "「不同意」三个字不构成一次可追溯的审批");
        }

        LocalDateTime validUntil = approved ? now.plusMinutes(properties.getGrantTtlMinutes()) : null;
        // 用 update(entity, wrapper) 而不是 update(null, wrapper)：后者会**跳过**
        // update_by/update_time 的自动填充（MP 的已知行为），审批痕就会缺掉"谁在什么时候改的"。
        // 同时实体本身可被单测断言——「批准时算出来的授权到期时间对不对」才测得到。
        AigCallApproval changed = new AigCallApproval();
        changed.setStatus(approved
            ? AigApprovalStatusEnum.APPROVED.getCode() : AigApprovalStatusEnum.REJECTED.getCode());
        changed.setApproverId(approverId);
        changed.setApproverName(approverName);
        changed.setDecidedAt(now);
        changed.setDecisionRemark(StringUtils.substring(bo.getRemark(), 0, REMARK_MAX));
        changed.setValidUntil(validUntil);
        int rows = approvalMapper.update(changed, new LambdaUpdateWrapper<AigCallApproval>()
            .eq(AigCallApproval::getApprovalId, approvalId)
            .eq(AigCallApproval::getStatus, AigApprovalStatusEnum.PENDING.getCode()));
        if (rows == 0) {
            // 与前面那次读之间的窗口内被别人处理了：宁可失败，不覆盖别人的结论
            throw new ServiceException("该申请单已被并发处理，请刷新后重试：approvalId=" + approvalId);
        }
        log.info("调用授权审批, approvalId={}, approved={}, approverId={}, 授权有效期止={}",
            approvalId, approved, approverId, validUntil);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long approvalId, Long requesterId) {
        if (approvalId == null) {
            throw new ServiceException("审批单ID不能为空");
        }
        if (requesterId == null) {
            throw new ServiceException("撤回人不能为空");
        }
        AigCallApproval row = approvalMapper.selectById(approvalId);
        if (row == null) {
            throw new ServiceException("审批单不存在：" + approvalId);
        }
        if (row.getRequesterId() == null || !row.getRequesterId().equals(requesterId)) {
            throw new ServiceException("只能撤回自己提交的申请：approvalId=" + approvalId);
        }
        AigApprovalStatusEnum status = AigApprovalStatusEnum.find(row.getStatus());
        if (status != AigApprovalStatusEnum.PENDING) {
            throw new ServiceException("只有「待审批」的申请可以撤回：当前状态="
                + (status == null ? row.getStatus() : status.getDesc()));
        }
        AigCallApproval changed = new AigCallApproval();
        changed.setStatus(AigApprovalStatusEnum.CANCELLED.getCode());
        int rows = approvalMapper.update(changed, new LambdaUpdateWrapper<AigCallApproval>()
            .eq(AigCallApproval::getApprovalId, approvalId)
            .eq(AigCallApproval::getStatus, AigApprovalStatusEnum.PENDING.getCode()));
        if (rows == 0) {
            throw new ServiceException("该申请单已被并发处理，请刷新后重试：approvalId=" + approvalId);
        }
        log.info("调用授权申请撤回, approvalId={}, requesterId={}", approvalId, requesterId);
    }

    @Override
    public boolean hasValidGrant(Long requesterId, String capabilityCode, String dataLevel) {
        if (requesterId == null || StringUtils.isBlank(capabilityCode) || StringUtils.isBlank(dataLevel)) {
            // 没有调用人（调度/系统发起）就没有可归属的授权；能力/等级不全时也无从判定
            return false;
        }
        Long count = approvalMapper.selectCount(new LambdaQueryWrapper<AigCallApproval>()
            .eq(AigCallApproval::getRequesterId, requesterId)
            .eq(AigCallApproval::getCapabilityCode, capabilityCode)
            .eq(AigCallApproval::getDataLevel, dataLevel)
            .eq(AigCallApproval::getStatus, AigApprovalStatusEnum.APPROVED.getCode())
            .gt(AigCallApproval::getValidUntil, LocalDateTime.now()));
        return count != null && count > 0L;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigCallApprovalSweepVo expireOverdue() {
        LocalDateTime now = LocalDateTime.now();
        AigCallApproval expiredRow = new AigCallApproval();
        expiredRow.setStatus(AigApprovalStatusEnum.EXPIRED.getCode());
        int expired = approvalMapper.update(expiredRow, new LambdaUpdateWrapper<AigCallApproval>()
            .eq(AigCallApproval::getStatus, AigApprovalStatusEnum.PENDING.getCode())
            .le(AigCallApproval::getExpireTime, now));
        AigCallApprovalSweepVo vo = new AigCallApprovalSweepVo();
        vo.setExpired(expired);
        vo.setScannedAt(now);
        if (expired > 0) {
            log.info("审批单超时扫描：{} 张超过审批时限，已置为「已超时」", expired);
        }
        return vo;
    }

}
