package org.dromara.aigov.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.agent.domain.AigReleaseEvent;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.evaluation.AigCanaryEvidence;
import org.dromara.aigov.agent.mapper.AigReleaseEventMapper;
import org.dromara.aigov.agent.service.IAigCanaryEvidenceService;
import org.dromara.aigov.config.AigCanaryProperties;
import org.dromara.aigov.domain.vo.AigErrorClassCountVo;
import org.dromara.aigov.mapper.AigInvocationAuditMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 灰度达标证据的取数实现。
 *
 * <p>本类<b>只负责取数</b>：哪一行算"这个版本的调用"、窗口从哪开始；
 * 达标与否的判定全在 {@link AigCanaryEvidence#evaluate}（纯函数，可单独测边界）。</p>
 *
 * @author ai-gov
 */
@Service
@RequiredArgsConstructor
public class AigCanaryEvidenceService implements IAigCanaryEvidenceService {

    private final AigInvocationAuditMapper auditMapper;

    private final AigReleaseEventMapper releaseEventMapper;

    private final AigCanaryProperties properties;

    @Override
    public AigCanaryEvidence canaryEvidence(String targetType, Long targetVersionId) {
        AigCanaryEvidence.Thresholds thresholds = thresholds();
        AigReleaseTargetTypeEnum type = AigReleaseTargetTypeEnum.find(targetType);
        if (type == null || targetVersionId == null) {
            return AigCanaryEvidence.unavailable(
                "对象类型或版本ID为空，无法确定灰度窗口（targetType=" + targetType
                    + "，targetVersionId=" + targetVersionId + "）", thresholds);
        }

        LocalDateTime from = canaryWindowStart(type.getCode(), targetVersionId);
        if (from == null) {
            return AigCanaryEvidence.unavailable("该版本在发布事件账本里没有「进入 CANDIDATE」的记录，"
                + "灰度窗口无法确定：没有灰度期就无从统计灰度表现"
                + "（先把它推进到 CANDIDATE，让调用真的发生过一段时间）", thresholds);
        }

        LocalDateTime to = LocalDateTime.now();
        long total = auditMapper.countByAgentVersionSince(targetVersionId, from);
        long failed = auditMapper.countFailedByAgentVersionSince(targetVersionId, from);
        Map<String, Long> byClass = errorClassCounts(targetVersionId, from);
        return AigCanaryEvidence.evaluate(from, to, total, failed, byClass, thresholds);
    }

    /**
     * 取灰度窗口起点＝该对象进入 CANDIDATE 的那条发布事件时间。
     *
     * <p>按 {@code eventId} 倒序取最新一条：账本是追加型的，eventId 的先后就是发生顺序，
     * 不依赖 {@code operate_time} 是否有值。CANDIDATE 状态没有回边（版本不可变、
     * 失败停在原地或停用），所以正常至多一条；真有多条时取最后一条更符合直觉。</p>
     *
     * @param targetType 对象类型编码
     * @param id         对象版本ID
     * @return 窗口起点；无记录或时间缺失时返回 null
     */
    private LocalDateTime canaryWindowStart(String targetType, Long id) {
        AigReleaseEvent latest = releaseEventMapper.selectOne(new LambdaQueryWrapper<AigReleaseEvent>()
            .eq(AigReleaseEvent::getTargetType, targetType)
            .eq(AigReleaseEvent::getTargetVersionId, id)
            .eq(AigReleaseEvent::getToStatus, AigReleaseStatusEnum.CANDIDATE.getCode())
            .orderByDesc(AigReleaseEvent::getEventId)
            .last("limit 1"));
        return latest == null ? null : latest.getOperateTime();
    }

    /**
     * 取窗口内各错误分类的次数（只查非空的 error_class）。
     *
     * <p>把分组结果整份取回、由 Java 筛"哪些算严重"，而不是在 SQL 里写死严重分类的 IN 列表：
     * 严重分类的口径只应有一处（{@link AigCanaryEvidence#SEVERE_CLASS_CODES}），
     * 写进 SQL 就会变成两处、迟早不一致。分组结果的基数很小（错误分类是个位数枚举）。</p>
     *
     * @param id   对象版本ID（= agent_version_id）
     * @param from 窗口起点
     * @return 分类 → 次数
     */
    private Map<String, Long> errorClassCounts(Long id, LocalDateTime from) {
        List<AigErrorClassCountVo> rows = auditMapper.listErrorClassCountsSince(id, from);
        Map<String, Long> byClass = new LinkedHashMap<>();
        if (rows == null) {
            return byClass;
        }
        for (AigErrorClassCountVo row : rows) {
            if (row == null || row.getErrorClass() == null) {
                continue;
            }
            byClass.put(row.getErrorClass(), row.getErrorCount() == null ? 0L : row.getErrorCount());
        }
        return byClass;
    }

    /**
     * 组装本次判定采用的阈值。
     *
     * @return 阈值
     */
    private AigCanaryEvidence.Thresholds thresholds() {
        return new AigCanaryEvidence.Thresholds(properties.getMinInvocations(),
            properties.getFailureRateLimit(), properties.getSevereErrorsAllowed());
    }

}
