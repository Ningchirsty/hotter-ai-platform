package org.dromara.talent.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.talent.domain.TlSensitiveAudit;
import org.dromara.talent.domain.vo.TlSensitiveAuditVo;
import org.dromara.talent.enums.TalentRoleEnum;
import org.dromara.talent.helper.TalentScopeHelper;
import org.dromara.talent.mapper.TlSensitiveAuditMapper;
import org.dromara.talent.service.ITalentAuditService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 敏感操作审计查询实现。
 * <p>
 * <b>数据范围口径（刻意为之）</b>：{@code tl_sensitive_audit} 没有 {@code region_code}，
 * 且审计的职责恰恰是记录"跨区域访问"本身，按区域收窄会让审计员看不到越权痕迹。
 * 因此这里仅做权限级约束（Controller 上的 {@code talent:audit:list}）：
 * <ul>
 *     <li>超管 / 集团管理员 / 审计员 → 可查全量；</li>
 *     <li>其余角色（区域 HR）→ 只能看到"关联人才落在其可见区域内"的记录，
 *     无区域权限者（仅查阅者）返回空页且不查库。</li>
 * </ul>
 *
 * @author talent
 */
@Service
@RequiredArgsConstructor
public class TalentAuditServiceImpl implements ITalentAuditService {

    /**
     * 审计 Mapper。
     */
    private final TlSensitiveAuditMapper auditMapper;

    /**
     * 数据范围与单条授权。
     */
    private final TalentScopeHelper scopeHelper;

    /**
     * 审计分页查询。
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @Override
    public PageResult<TlSensitiveAuditVo> queryPage(TlSensitiveAuditVo query, PageQuery pageQuery) {
        LambdaQueryWrapper<TlSensitiveAudit> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.eq(query.getOperatorId() != null, TlSensitiveAudit::getOperatorId, query.getOperatorId())
                .like(StringUtils.isNotBlank(query.getOperatorName()), TlSensitiveAudit::getOperatorName, query.getOperatorName())
                .eq(StringUtils.isNotBlank(query.getAction()), TlSensitiveAudit::getAction, query.getAction())
                .eq(StringUtils.isNotBlank(query.getTargetType()), TlSensitiveAudit::getTargetType, query.getTargetType())
                .eq(query.getTargetId() != null, TlSensitiveAudit::getTargetId, query.getTargetId())
                .eq(query.getTalentId() != null, TlSensitiveAudit::getTalentId, query.getTalentId())
                .eq(StringUtils.isNotBlank(query.getResult()), TlSensitiveAudit::getResult, query.getResult());
        }
        if (!canSeeAll()) {
            List<String> visibleRegions = scopeHelper.currentVisibleRegions();
            if (CollUtil.isEmpty(visibleRegions)) {
                return PageResult.build(List.of(), 0L);
            }
            // 只允许看"关联人才可见"的审计；导出等 talent_id 为空的记录对区域 HR 不可见
            wrapper.isNotNull(TlSensitiveAudit::getTalentId)
                .inSql(TlSensitiveAudit::getTalentId,
                    "select talent_id from tl_talent where del_flag = '0' and region_code in (" + regionIn(visibleRegions) + ")");
        }
        wrapper.orderByDesc(TlSensitiveAudit::getOperateTime);
        Page<TlSensitiveAudit> page = pageQuery.build();
        Page<TlSensitiveAuditVo> voPage = auditMapper.selectVoPage(page, wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    /**
     * 是否可查看全量审计。
     *
     * @return 是否全量可见
     */
    private boolean canSeeAll() {
        if (LoginHelper.isSuperAdmin()) {
            return true;
        }
        Set<String> roleKeys = scopeHelper.currentRoleKeys();
        return roleKeys.contains(TalentRoleEnum.ADMIN.getCode()) || roleKeys.contains(TalentRoleEnum.AUDITOR.getCode());
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
