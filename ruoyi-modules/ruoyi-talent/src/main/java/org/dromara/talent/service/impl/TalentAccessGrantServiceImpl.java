package org.dromara.talent.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.talent.domain.TlTalent;
import org.dromara.talent.domain.TlTalentAccessGrant;
import org.dromara.talent.domain.bo.TlAccessGrantBo;
import org.dromara.talent.enums.AuditActionEnum;
import org.dromara.talent.enums.AuditTargetTypeEnum;
import org.dromara.talent.enums.GrantPermissionEnum;
import org.dromara.talent.enums.GranteeTypeEnum;
import org.dromara.talent.helper.TalentAuditRecorder;
import org.dromara.talent.helper.TalentScopeHelper;
import org.dromara.talent.mapper.TlTalentAccessGrantMapper;
import org.dromara.talent.mapper.TlTalentMapper;
import org.dromara.talent.service.ITalentAccessGrantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 单条 / 临时访问授权服务实现。
 * <p>
 * 授权本身是敏感操作：任何读写都先加载人才并做区域 / 单条授权校验，授权动作写 CREATE_GRANT / DELETE 审计。
 *
 * @author talent
 */
@Service
@RequiredArgsConstructor
public class TalentAccessGrantServiceImpl implements ITalentAccessGrantService {

    /**
     * 授权 Mapper。
     */
    private final TlTalentAccessGrantMapper accessGrantMapper;

    /**
     * 人才主档 Mapper。
     */
    private final TlTalentMapper talentMapper;

    /**
     * 数据范围与单条授权。
     */
    private final TalentScopeHelper scopeHelper;

    /**
     * 审计记录器。
     */
    private final TalentAuditRecorder auditRecorder;

    /**
     * 某人才的全部授权。
     *
     * @param talentId 人才ID
     * @return 授权列表
     */
    @Override
    public List<TlTalentAccessGrant> listByTalent(Long talentId) {
        TlTalent talent = loadTalent(talentId);
        scopeHelper.checkTalentVisible(talent);
        return accessGrantMapper.selectList(new LambdaQueryWrapper<TlTalentAccessGrant>()
            .eq(TlTalentAccessGrant::getTalentId, talentId)
            .orderByDesc(TlTalentAccessGrant::getGrantTime));
    }

    /**
     * 新建授权。
     *
     * @param bo 授权参数
     * @return 授权ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TlAccessGrantBo bo) {
        TlTalent talent = loadTalent(bo.getTalentId());
        scopeHelper.checkTalentVisible(talent);
        if (!scopeHelper.canWriteRegion(talent.getRegionCode())) {
            throw new ServiceException("无权对该人才档案授权");
        }
        if (StringUtils.isBlank(bo.getGranteeType()) || GranteeTypeEnum.find(bo.getGranteeType()) == null) {
            throw new ServiceException("非法的被授权主体类型");
        }
        if (bo.getGranteeId() == null) {
            throw new ServiceException("被授权主体ID不能为空");
        }
        if (CollUtil.isEmpty(bo.getPermissions())) {
            throw new ServiceException("授权动作不能为空");
        }
        List<String> normalized = new ArrayList<>(bo.getPermissions().size());
        for (String permission : bo.getPermissions()) {
            if (StringUtils.isBlank(permission) || GrantPermissionEnum.find(permission.trim().toUpperCase()) == null) {
                throw new ServiceException("非法的授权动作：" + permission);
            }
            normalized.add(permission.trim().toUpperCase());
        }
        if (bo.getStartTime() != null && bo.getEndTime() != null && !bo.getEndTime().isAfter(bo.getStartTime())) {
            throw new ServiceException("失效时间必须晚于生效时间");
        }
        TlTalentAccessGrant entity = new TlTalentAccessGrant();
        entity.setTalentId(talent.getTalentId());
        entity.setGranteeType(bo.getGranteeType().trim().toUpperCase());
        entity.setGranteeId(bo.getGranteeId());
        entity.setPermission(String.join(",", normalized));
        entity.setStartTime(bo.getStartTime());
        entity.setEndTime(bo.getEndTime());
        entity.setGrantBy(LoginHelper.getUserId());
        entity.setGrantTime(LocalDateTime.now());
        entity.setReason(StringUtils.substring(StringUtils.defaultString(bo.getReason()), 0, 255));
        entity.setStatus("0");
        accessGrantMapper.insert(entity);
        auditRecorder.record(AuditActionEnum.CREATE_GRANT, AuditTargetTypeEnum.GRANT, entity.getGrantId(),
            talent.getTalentId(), true, "新增单条访问授权");
        return entity.getGrantId();
    }

    /**
     * 撤销授权。
     *
     * @param grantId 授权ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long grantId) {
        TlTalentAccessGrant grant = grantId == null ? null : accessGrantMapper.selectById(grantId);
        if (grant == null || "1".equals(grant.getDelFlag())) {
            throw new ServiceException("授权记录不存在");
        }
        TlTalent talent = loadTalent(grant.getTalentId());
        scopeHelper.checkTalentVisible(talent);
        if (!scopeHelper.canWriteRegion(talent.getRegionCode())) {
            throw new ServiceException("无权重置该人才档案的授权");
        }
        TlTalentAccessGrant entity = new TlTalentAccessGrant();
        entity.setGrantId(grantId);
        entity.setStatus("1");
        accessGrantMapper.updateById(entity);
        auditRecorder.record(AuditActionEnum.DELETE, AuditTargetTypeEnum.GRANT, grantId, talent.getTalentId(), true, "撤销单条访问授权");
    }

    /**
     * 加载人才，不存在直接抛业务异常。
     *
     * @param talentId 人才ID
     * @return 人才实体
     */
    private TlTalent loadTalent(Long talentId) {
        if (talentId == null) {
            throw new ServiceException(TalentScopeHelper.TALENT_NOT_FOUND);
        }
        TlTalent talent = talentMapper.selectById(talentId);
        if (talent == null || "1".equals(talent.getDelFlag())) {
            throw new ServiceException(TalentScopeHelper.TALENT_NOT_FOUND);
        }
        return talent;
    }

}
