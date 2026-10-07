package org.dromara.aigov.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.AigUserQuota;
import org.dromara.aigov.domain.bo.AigUserQuotaBo;
import org.dromara.aigov.domain.vo.AigUserQuotaUsageVo;
import org.dromara.aigov.domain.vo.AigUserQuotaVo;
import org.dromara.aigov.mapper.AigInvocationAuditMapper;
import org.dromara.aigov.mapper.AigUserQuotaMapper;
import org.dromara.aigov.service.IAigUserQuotaService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.core.domain.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 调用人均配额实现（C3：用量配额按人）。
 *
 * <h3>三处刻意做对的地方</h3>
 * <ol>
 *     <li><b>单位是「调用次数」而不是钱</b>：审计里的 {@code cost}「为空表示未知而非免费」，
 *         经 snail-ai 的链路恒为 null。用经常未知的数字做配额 = 一本对不上的账
 *         （这正是当初「累计预算刻意不做」的理由）。次数是每条调用都有的事实。</li>
 *     <li><b>没有配额行 = 不限</b>：新表上线不改变任何既有调用行为。要给谁设额度就给他配一行，
 *         而不是「默认给所有人一个额度」——后者会把一次上线变成一次全量限流。</li>
 *     <li><b>计数含失败</b>：失败的那次调用同样占用了调用机会（供应商可能已经跑过、也可能只是超时）；
 *         只算成功会让「反复失败重试」成为绕开配额的路子。</li>
 * </ol>
 *
 * <h3>一个必须写在代码里的陷阱</h3>
 * <p>{@code updateById} 默认策略是 {@code NOT_NULL}，即<b>跳过 null 字段</b>。
 * 而本表的「不限」就是 null——所以用它更新会导致<b>「把日上限清成不限」静默失败</b>
 * （界面上显示清了，库里还是旧上限）。因此需要在更新时把 null 用显式
 * {@code set(...)} 写进去，见 {@link #save}。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigUserQuotaServiceImpl implements IAigUserQuotaService {

    /**
     * 记录状态：正常（参与判定）
     */
    private static final String STATUS_NORMAL = "0";

    private final AigUserQuotaMapper quotaMapper;

    /**
     * 用量来源：调用审计表（唯一逐次落库的事实记录）。
     */
    private final AigInvocationAuditMapper auditMapper;

    @Override
    public PageResult<AigUserQuotaVo> queryPage(AigUserQuotaBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AigUserQuota> wrapper = new LambdaQueryWrapper<AigUserQuota>()
            .eq(bo.getUserId() != null, AigUserQuota::getUserId, bo.getUserId())
            .like(StringUtils.isNotBlank(bo.getUserName()), AigUserQuota::getUserName, bo.getUserName())
            .eq(StringUtils.isNotBlank(bo.getStatus()), AigUserQuota::getStatus, bo.getStatus())
            .orderByDesc(AigUserQuota::getUpdateTime);
        return PageResult.build(quotaMapper.selectVoPage(pageQuery.build(), wrapper));
    }

    @Override
    public AigUserQuotaUsageVo usage(Long userId) {
        if (userId == null) {
            throw new ServiceException("用户不能为空");
        }
        LocalDateTime dayFrom = LocalDate.now().atStartOfDay();
        LocalDateTime monthFrom = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        AigUserQuota quota = selectByUser(userId);
        Integer dailyLimit = quota == null ? null : quota.getDailyLimit();
        Integer monthlyLimit = quota == null ? null : quota.getMonthlyLimit();
        long dailyUsed = auditMapper.countByCallerSince(userId, dayFrom);
        long monthlyUsed = auditMapper.countByCallerSince(userId, monthFrom);
        boolean limited = quota != null && STATUS_NORMAL.equals(quota.getStatus())
            && (dailyLimit != null || monthlyLimit != null);
        // 「达到上限」即不可再调：上限是「允许的最大次数」
        boolean exceeded = limited
            && ((dailyLimit != null && dailyUsed >= dailyLimit)
                || (monthlyLimit != null && monthlyUsed >= monthlyLimit));
        return new AigUserQuotaUsageVo(userId, quota == null ? null : quota.getUserName(),
            dailyLimit, dailyUsed, dayFrom, monthlyLimit, monthlyUsed, monthFrom, limited, exceeded);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(AigUserQuotaBo bo) {
        if (bo == null || bo.getUserId() == null) {
            throw new ServiceException("用户不能为空");
        }
        AigUserQuota existing = selectByUser(bo.getUserId());
        String status = StringUtils.isBlank(bo.getStatus()) ? STATUS_NORMAL : bo.getStatus();
        if (existing == null) {
            AigUserQuota entity = new AigUserQuota();
            entity.setUserId(bo.getUserId());
            entity.setUserName(StringUtils.substring(bo.getUserName(), 0, 64));
            entity.setDailyLimit(bo.getDailyLimit());
            entity.setMonthlyLimit(bo.getMonthlyLimit());
            entity.setStatus(status);
            entity.setRemark(StringUtils.substring(bo.getRemark(), 0, 500));
            quotaMapper.insert(entity);
            log.info("人均配额新增, userId={}, daily={}, monthly={}, operator={}",
                bo.getUserId(), bo.getDailyLimit(), bo.getMonthlyLimit(), bo.getUserId());
            return entity.getQuotaId();
        }

        // 非空列走实体更新（顺带让 update_by/update_time 自动填充）
        AigUserQuota entity = new AigUserQuota();
        entity.setQuotaId(existing.getQuotaId());
        entity.setUserName(StringUtils.substring(bo.getUserName(), 0, 64));
        entity.setStatus(status);
        entity.setRemark(StringUtils.substring(bo.getRemark(), 0, 500));
        quotaMapper.updateById(entity);
        // null 是「不限」的表示，而 updateById 会跳过 null：需要清成不限时必须显式 set 写进去，
        // 否则界面上「清空」了、库里还是旧上限（静默失败）
        if (bo.getDailyLimit() == null || bo.getMonthlyLimit() == null) {
            LambdaUpdateWrapper<AigUserQuota> clear = new LambdaUpdateWrapper<AigUserQuota>()
                .eq(AigUserQuota::getQuotaId, existing.getQuotaId());
            if (bo.getDailyLimit() == null) {
                clear.set(AigUserQuota::getDailyLimit, null);
            }
            if (bo.getMonthlyLimit() == null) {
                clear.set(AigUserQuota::getMonthlyLimit, null);
            }
            quotaMapper.update(null, clear);
        }
        log.info("人均配额更新, quotaId={}, userId={}, daily={}, monthly={}",
            existing.getQuotaId(), bo.getUserId(), bo.getDailyLimit(), bo.getMonthlyLimit());
        return existing.getQuotaId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long quotaId) {
        if (quotaId == null) {
            throw new ServiceException("配额ID不能为空");
        }
        AigUserQuota existing = quotaMapper.selectById(quotaId);
        if (existing == null) {
            throw new ServiceException("配额不存在：" + quotaId);
        }
        quotaMapper.deleteById(quotaId);
        log.info("人均配额删除（回到「不限」）, quotaId={}, userId={}", quotaId, existing.getUserId());
    }

    @Override
    public void assertWithinQuota(Long callerId) {
        if (callerId == null || callerId <= 0L) {
            // 无「人」可归属（调度/系统发起，审计里 caller_id 为空）：人均配额不适用。
            // 这不是漏判：人均配额的作用域就是「人」；系统调用该由别的口径管（如任务预算）。
            return;
        }
        AigUserQuotaUsageVo usage = usage(callerId);
        if (!usage.limited() || !usage.exceeded()) {
            return;
        }
        String reason = exceedMessage(usage);
        log.warn("调用被人均配额拦截, callerId={}, daily={}/{}, monthly={}/{}",
            callerId, usage.dailyUsed(), usage.dailyLimit(), usage.monthlyUsed(), usage.monthlyLimit());
        throw new ServiceException(reason);
    }

    /**
     * 组装超限原因（必须说清：哪个周期超了、用了多少、上限多少、单位是什么）。
     *
     * @param usage 用量
     * @return 可读原因
     */
    private static String exceedMessage(AigUserQuotaUsageVo usage) {
        StringBuilder sb = new StringBuilder();
        sb.append("调用人的用量配额已用尽（配额按「调用次数」统计，含失败调用，按自然周期重置）：");
        if (usage.dailyLimit() != null) {
            sb.append("今日 ").append(usage.dailyUsed()).append('/').append(usage.dailyLimit())
                .append(" 次（自 ").append(usage.dailyFrom()).append(" 起）");
        }
        if (usage.monthlyLimit() != null) {
            if (usage.dailyLimit() != null) {
                sb.append("；");
            }
            sb.append("本月 ").append(usage.monthlyUsed()).append('/').append(usage.monthlyLimit())
                .append(" 次（自 ").append(usage.monthlyFrom()).append(" 起）");
        }
        sb.append("。如需更高额度，请联系 AI 管理员调整人均配额配置");
        return sb.toString();
    }

    /**
     * 按用户取配额行（一人一行）。
     *
     * @param userId 用户ID
     * @return 配额行；无则 null
     */
    private AigUserQuota selectByUser(Long userId) {
        return quotaMapper.selectOne(new LambdaQueryWrapper<AigUserQuota>()
            .eq(AigUserQuota::getUserId, userId));
    }

}
