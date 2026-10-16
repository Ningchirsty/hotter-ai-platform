package org.dromara.aigov.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.aigov.domain.bo.AigRoutePolicyBo;
import org.dromara.aigov.domain.vo.AigRoutePolicyVo;
import org.dromara.aigov.helper.AigHighRiskChanges;
import org.dromara.aigov.helper.AigHighRiskGuard;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.mapper.AigRoutePolicyMapper;
import org.dromara.aigov.service.IAigRoutePolicyService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
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
 * AI 路由策略服务实现。
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigRoutePolicyServiceImpl implements IAigRoutePolicyService {

    /**
     * 状态：正常。
     */
    private static final String STATUS_NORMAL = "0";

    /**
     * 路由策略 Mapper。
     */
    private final AigRoutePolicyMapper routePolicyMapper;

    /**
     * 能力目录 Mapper（回填 capabilityName 展示字段）。
     */
    private final AigCapabilityMapper capabilityMapper;

    /**
     * 高风险变更闸门（Q1C）：放宽外发 / 动限制级策略 / 删限制行，需要额外权限点。
     */
    private final AigHighRiskGuard highRiskGuard;

    @Override
    public PageResult<AigRoutePolicyVo> queryPage(AigRoutePolicyBo bo, PageQuery pageQuery) {
        AigRoutePolicyBo query = bo == null ? new AigRoutePolicyBo() : bo;
        LambdaQueryWrapper<AigRoutePolicy> wrapper = new LambdaQueryWrapper<AigRoutePolicy>()
            .eq(StringUtils.isNotBlank(query.getCapabilityCode()), AigRoutePolicy::getCapabilityCode, query.getCapabilityCode())
            .eq(StringUtils.isNotBlank(query.getDataLevel()), AigRoutePolicy::getDataLevel, query.getDataLevel())
            .eq(StringUtils.isNotBlank(query.getPreferredDeployment()), AigRoutePolicy::getPreferredDeployment, query.getPreferredDeployment())
            .eq(StringUtils.isNotBlank(query.getAllowExternal()), AigRoutePolicy::getAllowExternal, query.getAllowExternal())
            .eq(StringUtils.isNotBlank(query.getStatus()), AigRoutePolicy::getStatus, query.getStatus())
            .orderByAsc(AigRoutePolicy::getCapabilityCode)
            .orderByAsc(AigRoutePolicy::getDataLevel);
        Page<AigRoutePolicyVo> voPage = routePolicyMapper.selectVoPage(pageQuery.build(), wrapper);
        List<AigRoutePolicyVo> rows = voPage.getRecords();
        fillCapabilityName(rows);
        return PageResult.build(rows, voPage.getTotal());
    }

    @Override
    public AigRoutePolicyVo getDetail(Long policyId) {
        if (policyId == null) {
            throw new ServiceException("策略ID不能为空");
        }
        AigRoutePolicyVo vo = routePolicyMapper.selectVoById(policyId);
        if (vo == null) {
            throw new ServiceException("路由策略不存在");
        }
        return vo;
    }

    @Override
    public Long create(AigRoutePolicyBo bo) {
        String capabilityCode = bo.getCapabilityCode() == null ? null : bo.getCapabilityCode().trim();
        if (existsPolicy(capabilityCode, bo.getDataLevel(), null)) {
            throw new ServiceException("该能力在 " + bo.getDataLevel() + " 数据等级下已配置路由策略");
        }
        // Q1C：新增一条放行外发（或限制级/钉外部部署）的策略 = 放宽数据出界，需要高风险权限
        if (AigHighRiskChanges.forNewPolicy(bo)) {
            highRiskGuard.assertAllowed("新增一条会放行外发或作用于限制级/严格级数据的路由策略");
        }
        AigRoutePolicy entity = BeanUtil.copyProperties(bo, AigRoutePolicy.class);
        entity.setPolicyId(null);
        entity.setCapabilityCode(capabilityCode);
        entity.setStatus(StringUtils.isBlank(bo.getStatus()) ? STATUS_NORMAL : bo.getStatus());
        routePolicyMapper.insert(entity);
        return entity.getPolicyId();
    }

    @Override
    public void update(AigRoutePolicyBo bo) {
        AigRoutePolicy exist = loadPolicy(bo.getPolicyId());
        String capabilityCode = StringUtils.isBlank(bo.getCapabilityCode())
            ? exist.getCapabilityCode() : bo.getCapabilityCode().trim();
        String dataLevel = StringUtils.isBlank(bo.getDataLevel()) ? exist.getDataLevel() : bo.getDataLevel();
        if (existsPolicy(capabilityCode, dataLevel, bo.getPolicyId())) {
            throw new ServiceException("该能力在 " + dataLevel + " 数据等级下已配置路由策略");
        }
        // Q1C：判"算不算放宽"必须看改之前那一行——N→Y 只有对比新旧才看得出来。
        // 收紧（例如把外发关掉）不算高风险：否则这个权限点会变成"改策略都要申请"，
        // 人就绕到别的入口去了，反而更难管。
        if (AigHighRiskChanges.forUpdatedPolicy(exist, bo)) {
            highRiskGuard.assertAllowed("修改一条会放行外发或作用于限制级/严格级数据的路由策略");
        }
        AigRoutePolicy entity = BeanUtil.copyProperties(bo, AigRoutePolicy.class);
        entity.setPolicyId(exist.getPolicyId());
        entity.setCapabilityCode(capabilityCode);
        entity.setDataLevel(dataLevel);
        routePolicyMapper.updateById(entity);
    }

    @Override
    public void remove(Long policyId) {
        AigRoutePolicy exist = loadPolicy(policyId);
        // Q1C：删掉一条"仅本地/限制级"的策略，效果与放开外发相同——所以删除同样要过闸门
        if (AigHighRiskChanges.forRemovedPolicy(exist)) {
            highRiskGuard.assertAllowed("删除一条会放行外发或作用于限制级/严格级数据的路由策略");
        }
        routePolicyMapper.deleteById(policyId);
    }

    /**
     * 回填能力名称（策略表只存编码）。
     *
     * @param rows 策略视图列表
     */
    private void fillCapabilityName(List<AigRoutePolicyVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        Set<String> codes = new LinkedHashSet<>();
        for (AigRoutePolicyVo row : rows) {
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
        for (AigRoutePolicyVo row : rows) {
            row.setCapabilityName(nameMap.get(row.getCapabilityCode()));
        }
    }

    /**
     * 加载策略，不存在时抛异常。
     *
     * @param policyId 策略ID
     * @return 策略实体
     */
    private AigRoutePolicy loadPolicy(Long policyId) {
        if (policyId == null) {
            throw new ServiceException("策略ID不能为空");
        }
        AigRoutePolicy entity = routePolicyMapper.selectById(policyId);
        if (entity == null) {
            throw new ServiceException("路由策略不存在");
        }
        return entity;
    }

    /**
     * 策略是否已存在（能力 × 数据等级唯一）。
     *
     * @param capabilityCode 能力编码
     * @param dataLevel      数据等级
     * @param excludeId      需要排除的策略ID
     * @return 是否已存在
     */
    private boolean existsPolicy(String capabilityCode, String dataLevel, Long excludeId) {
        if (StringUtils.isBlank(capabilityCode) || StringUtils.isBlank(dataLevel)) {
            return false;
        }
        LambdaQueryWrapper<AigRoutePolicy> wrapper = new LambdaQueryWrapper<AigRoutePolicy>()
            .eq(AigRoutePolicy::getCapabilityCode, capabilityCode)
            .eq(AigRoutePolicy::getDataLevel, dataLevel)
            .ne(excludeId != null, AigRoutePolicy::getPolicyId, excludeId);
        return routePolicyMapper.selectCount(wrapper) > 0;
    }

}
