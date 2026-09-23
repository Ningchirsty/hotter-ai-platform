package org.dromara.content.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.CpGateRule;
import org.dromara.content.domain.bo.ContentGateRuleBo;
import org.dromara.content.domain.vo.CpGateRuleVo;
import org.dromara.content.enums.ContentGateLevelEnum;
import org.dromara.content.mapper.CpGateRuleMapper;
import org.dromara.content.service.IContentGateRuleService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 闸门规则服务实现。
 *
 * @author content
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentGateRuleServiceImpl implements IContentGateRuleService {

    /**
     * 启用
     */
    private static final String ENABLED = "0";

    /**
     * 规则 Mapper
     */
    private final CpGateRuleMapper gateRuleMapper;

    @Override
    public PageResult<CpGateRuleVo> queryPage(ContentGateRuleBo bo, PageQuery pageQuery) {
        ContentGateRuleBo query = bo == null ? new ContentGateRuleBo() : bo;
        LambdaQueryWrapper<CpGateRule> wrapper = new LambdaQueryWrapper<CpGateRule>()
            .eq(StringUtils.isNotBlank(query.getQueryDeliverableType()),
                CpGateRule::getDeliverableType, query.getQueryDeliverableType())
            .like(StringUtils.isNotBlank(query.getFieldCode()), CpGateRule::getFieldCode, query.getFieldCode())
            .eq(StringUtils.isNotBlank(query.getGateLevel()), CpGateRule::getGateLevel, query.getGateLevel())
            .eq(StringUtils.isNotBlank(query.getEnabled()), CpGateRule::getEnabled, query.getEnabled())
            .orderByAsc(CpGateRule::getDeliverableType)
            .orderByAsc(CpGateRule::getSortNo);
        var voPage = gateRuleMapper.selectVoPage(pageQuery.build(), wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    public CpGateRuleVo getDetail(Long ruleId) {
        CpGateRuleVo vo = gateRuleMapper.selectVoById(load(ruleId).getRuleId());
        if (vo == null) {
            throw new ServiceException("闸门规则不存在");
        }
        return vo;
    }

    @Override
    public Long create(ContentGateRuleBo bo) {
        checkLevel(bo.getGateLevel());
        CpGateRule entity = BeanUtil.copyProperties(bo, CpGateRule.class);
        entity.setRuleId(null);
        entity.setRequirePresent(StringUtils.isBlank(bo.getRequirePresent()) ? "Y" : bo.getRequirePresent());
        entity.setEnabled(StringUtils.isBlank(bo.getEnabled()) ? ENABLED : bo.getEnabled());
        entity.setSortNo(bo.getSortNo() == null ? 0 : bo.getSortNo());
        gateRuleMapper.insert(entity);
        log.info("新增闸门规则, ruleId={}, type={}, field={}, level={}",
            entity.getRuleId(), entity.getDeliverableType(), entity.getFieldCode(), entity.getGateLevel());
        return entity.getRuleId();
    }

    @Override
    public void update(ContentGateRuleBo bo) {
        CpGateRule exist = load(bo.getRuleId());
        checkLevel(bo.getGateLevel());
        CpGateRule entity = BeanUtil.copyProperties(bo, CpGateRule.class);
        entity.setRuleId(exist.getRuleId());
        gateRuleMapper.updateById(entity);
    }

    @Override
    public void remove(Long ruleId) {
        load(ruleId);
        gateRuleMapper.deleteById(ruleId);
    }

    @Override
    public List<CpGateRule> listEnabledRules(String deliverableType) {
        if (StringUtils.isBlank(deliverableType)) {
            return List.of();
        }
        return gateRuleMapper.selectList(new LambdaQueryWrapper<CpGateRule>()
            .eq(CpGateRule::getDeliverableType, deliverableType)
            .eq(CpGateRule::getEnabled, ENABLED)
            .orderByAsc(CpGateRule::getSortNo));
    }

    @Override
    public java.util.Set<String> allEnabledFieldCodes() {
        java.util.Set<String> codes = new java.util.LinkedHashSet<>();
        for (CpGateRule rule : gateRuleMapper.selectList(new LambdaQueryWrapper<CpGateRule>()
            .eq(CpGateRule::getEnabled, ENABLED))) {
            if (StringUtils.isNotBlank(rule.getFieldCode())) {
                codes.add(rule.getFieldCode());
            }
        }
        return codes;
    }

    /**
     * 加载规则，不存在抛异常。
     *
     * @param ruleId 规则ID
     * @return 规则实体
     */
    private CpGateRule load(Long ruleId) {
        if (ruleId == null) {
            throw new ServiceException("规则ID不能为空");
        }
        CpGateRule entity = gateRuleMapper.selectById(ruleId);
        if (entity == null) {
            throw new ServiceException("闸门规则不存在");
        }
        return entity;
    }

    /**
     * 校验闸门等级取值。
     * <p>写错的等级不会报错、只会被闸门判定静默忽略，因此必须在此拦住。</p>
     *
     * @param gateLevel 等级编码
     */
    private void checkLevel(String gateLevel) {
        if (ContentGateLevelEnum.find(gateLevel) == null) {
            throw new ServiceException("闸门等级非法：" + gateLevel);
        }
    }

}
