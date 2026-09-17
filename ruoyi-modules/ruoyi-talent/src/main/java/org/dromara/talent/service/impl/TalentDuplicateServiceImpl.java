package org.dromara.talent.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.talent.domain.TlTalent;
import org.dromara.talent.domain.TlTalentDuplicate;
import org.dromara.talent.domain.bo.TlDuplicateConfirmBo;
import org.dromara.talent.domain.vo.TlTalentDuplicateVo;
import org.dromara.talent.enums.DuplicateConclusionEnum;
import org.dromara.talent.helper.TalentScopeHelper;
import org.dromara.talent.mapper.TlTalentDuplicateMapper;
import org.dromara.talent.mapper.TlTalentMapper;
import org.dromara.talent.service.ITalentDuplicateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 重复人才预警服务实现（只预警不自动合并）。
 *
 * @author talent
 */
@Service
@RequiredArgsConstructor
public class TalentDuplicateServiceImpl implements ITalentDuplicateService {

    /**
     * 重复预警 Mapper。
     */
    private final TlTalentDuplicateMapper duplicateMapper;

    /**
     * 人才主档 Mapper。
     */
    private final TlTalentMapper talentMapper;

    /**
     * 数据范围与单条授权。
     */
    private final TalentScopeHelper scopeHelper;

    /**
     * 分页查询重复预警。
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @Override
    public PageResult<TlTalentDuplicateVo> queryPage(TlTalentDuplicateVo query, PageQuery pageQuery) {
        List<String> visibleRegions = scopeHelper.currentVisibleRegions();
        if (CollUtil.isEmpty(visibleRegions)) {
            return PageResult.build(List.of(), 0L);
        }
        LambdaQueryWrapper<TlTalentDuplicate> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.eq(query.getSourceTalentId() != null, TlTalentDuplicate::getSourceTalentId, query.getSourceTalentId())
                .eq(query.getMatchedTalentId() != null, TlTalentDuplicate::getMatchedTalentId, query.getMatchedTalentId())
                .eq(StringUtils.isNotBlank(query.getMatchRule()), TlTalentDuplicate::getMatchRule, query.getMatchRule())
                .eq(StringUtils.isNotBlank(query.getConclusion()), TlTalentDuplicate::getConclusion, query.getConclusion());
        }
        // 数据范围：预警行的来源人才必须落在当前用户可见区域内（tl_talent_duplicate 本身无 region_code）
        wrapper.inSql(TlTalentDuplicate::getSourceTalentId,
            "select talent_id from tl_talent where del_flag = '0' and region_code in (" + regionIn(visibleRegions) + ")");
        wrapper.orderByDesc(TlTalentDuplicate::getCreateTime);
        Page<TlTalentDuplicate> page = pageQuery.build();
        Page<TlTalentDuplicateVo> voPage = duplicateMapper.selectVoPage(page, wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    /**
     * 人工确认重复结论。
     *
     * @param bo 确认参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(TlDuplicateConfirmBo bo) {
        TlTalentDuplicate duplicate = bo.getDuplicateId() == null ? null : duplicateMapper.selectById(bo.getDuplicateId());
        if (duplicate == null || "1".equals(duplicate.getDelFlag())) {
            throw new ServiceException("重复预警记录不存在");
        }
        DuplicateConclusionEnum conclusion = DuplicateConclusionEnum.find(bo.getConclusion());
        if (conclusion == null || DuplicateConclusionEnum.PENDING == conclusion) {
            throw new ServiceException("非法的确认结论");
        }
        TlTalent source = talentMapper.selectById(duplicate.getSourceTalentId());
        // 单条授权 / 区域范围校验：不允许确认自己看不到的人才预警
        scopeHelper.checkTalentVisible(source);
        TlTalentDuplicate entity = new TlTalentDuplicate();
        entity.setDuplicateId(duplicate.getDuplicateId());
        entity.setConclusion(conclusion.getCode());
        entity.setConfirmBy(LoginHelper.getUserId());
        entity.setConfirmTime(LocalDateTime.now());
        entity.setConfirmRemark(StringUtils.substring(StringUtils.defaultString(bo.getConfirmRemark()), 0, 255));
        duplicateMapper.updateById(entity);
    }

    /**
     * 构造安全的值列表（值来自枚举常量，不含外部输入）。
     *
     * @param regions 区域列表
     * @return SQL 值片段
     */
    private String regionIn(List<String> regions) {
        return regions.stream().map(r -> "'" + r + "'").collect(Collectors.joining(","));
    }

}
