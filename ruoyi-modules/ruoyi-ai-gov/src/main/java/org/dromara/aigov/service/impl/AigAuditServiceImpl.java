package org.dromara.aigov.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.AigInvocationAudit;
import org.dromara.aigov.domain.bo.AigAuditQueryBo;
import org.dromara.aigov.domain.vo.AigInvocationAuditVo;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.mapper.AigInvocationAuditMapper;
import org.dromara.aigov.service.IAigAuditService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 调用审计查询服务实现（只读，追加型审计表无逻辑删除）。
 * <p>时间范围兼容两种下发方式：显式 {@code beginTime/endTime} 字段，
 * 以及平台惯例的 {@code params[beginTime]}/{@code params[endTime]}（前端 {@code addDateRange}）。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigAuditServiceImpl implements IAigAuditService {

    /**
     * 时间起 key。
     */
    private static final String KEY_BEGIN_TIME = "beginTime";

    /**
     * 时间止 key。
     */
    private static final String KEY_END_TIME = "endTime";

    /**
     * 审计 Mapper。
     */
    private final AigInvocationAuditMapper auditMapper;

    /**
     * 能力目录 Mapper（回填 capabilityName 展示字段）。
     */
    private final AigCapabilityMapper capabilityMapper;

    @Override
    public PageResult<AigInvocationAuditVo> queryPage(AigAuditQueryBo bo, PageQuery pageQuery) {
        AigAuditQueryBo query = bo == null ? new AigAuditQueryBo() : bo;
        Object beginTime = resolveRange(query, KEY_BEGIN_TIME, query.getBeginTime());
        Object endTime = resolveRange(query, KEY_END_TIME, query.getEndTime());
        LambdaQueryWrapper<AigInvocationAudit> wrapper = new LambdaQueryWrapper<AigInvocationAudit>()
            .eq(StringUtils.isNotBlank(query.getTraceId()), AigInvocationAudit::getTraceId, query.getTraceId())
            .eq(StringUtils.isNotBlank(query.getCapabilityCode()), AigInvocationAudit::getCapabilityCode, query.getCapabilityCode())
            .eq(query.getCallerId() != null, AigInvocationAudit::getCallerId, query.getCallerId())
            .like(StringUtils.isNotBlank(query.getCallerName()), AigInvocationAudit::getCallerName, query.getCallerName())
            .eq(StringUtils.isNotBlank(query.getDataLevel()), AigInvocationAudit::getDataLevel, query.getDataLevel())
            .eq(query.getModelId() != null, AigInvocationAudit::getModelId, query.getModelId())
            .eq(StringUtils.isNotBlank(query.getModelKey()), AigInvocationAudit::getModelKey, query.getModelKey())
            .eq(StringUtils.isNotBlank(query.getExternalCall()), AigInvocationAudit::getExternalCall, query.getExternalCall())
            .eq(StringUtils.isNotBlank(query.getResult()), AigInvocationAudit::getResult, query.getResult())
            .eq(StringUtils.isNotBlank(query.getManualDecision()), AigInvocationAudit::getManualDecision, query.getManualDecision())
            .ge(beginTime != null, AigInvocationAudit::getOperateTime, beginTime)
            .le(endTime != null, AigInvocationAudit::getOperateTime, endTime)
            .orderByDesc(AigInvocationAudit::getOperateTime);
        Page<AigInvocationAuditVo> voPage = auditMapper.selectVoPage(pageQuery.build(), wrapper);
        List<AigInvocationAuditVo> rows = voPage.getRecords();
        fillCapabilityName(rows);
        return PageResult.build(rows, voPage.getTotal());
    }

    /**
     * 解析时间范围：优先取显式字段，其次取 {@code params} 中的平台惯例键。
     *
     * @param query        查询条件
     * @param key          params 中的键
     * @param explicitValue 显式字段值（可为 null）
     * @return 时间值（String 或 LocalDateTime），无值时返回 null
     */
    private Object resolveRange(AigAuditQueryBo query, String key, Object explicitValue) {
        if (explicitValue != null) {
            return explicitValue;
        }
        Map<String, Object> params = query.getParams();
        if (params == null || params.isEmpty()) {
            return null;
        }
        Object value = params.get(key);
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            return null;
        }
        return value;
    }

    /**
     * 回填能力名称（审计表只存编码，展示需要名称）。
     *
     * @param rows 审计视图列表
     */
    private void fillCapabilityName(List<AigInvocationAuditVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        Set<String> codes = new LinkedHashSet<>();
        for (AigInvocationAuditVo row : rows) {
            if (StringUtils.isNotBlank(row.getCapabilityCode())) {
                codes.add(row.getCapabilityCode());
            }
        }
        if (codes.isEmpty()) {
            return;
        }
        List<AigCapability> capabilities = capabilityMapper.selectList(new LambdaQueryWrapper<AigCapability>()
            .in(AigCapability::getCapabilityCode, new ArrayList<>(codes)));
        Map<String, String> nameMap = new HashMap<>();
        if (capabilities != null) {
            for (AigCapability capability : capabilities) {
                if (capability != null && StringUtils.isNotBlank(capability.getCapabilityCode())) {
                    nameMap.put(capability.getCapabilityCode(), capability.getCapabilityName());
                }
            }
        }
        for (AigInvocationAuditVo row : rows) {
            row.setCapabilityName(nameMap.get(row.getCapabilityCode()));
        }
    }

}
