package org.dromara.aigov.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.bo.AigCapabilityBo;
import org.dromara.aigov.domain.vo.AigCapabilityVo;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.service.IAigCapabilityService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

/**
 * AI 能力目录服务实现。
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigCapabilityServiceImpl implements IAigCapabilityService {

    /**
     * 状态：正常。
     */
    private static final String STATUS_NORMAL = "0";

    /**
     * 默认数据策略：本地优先。
     */
    private static final String DEFAULT_DATA_POLICY = "LOCAL_FIRST";

    /**
     * 默认审计等级：摘要。
     */
    private static final String DEFAULT_AUDIT_LEVEL = "SUMMARY";

    /**
     * 能力目录 Mapper。
     */
    private final AigCapabilityMapper capabilityMapper;

    @Override
    public PageResult<AigCapabilityVo> queryPage(AigCapabilityBo bo, PageQuery pageQuery) {
        AigCapabilityBo query = bo == null ? new AigCapabilityBo() : bo;
        LambdaQueryWrapper<AigCapability> wrapper = new LambdaQueryWrapper<AigCapability>()
            .like(StringUtils.isNotBlank(query.getCapabilityCode()), AigCapability::getCapabilityCode, query.getCapabilityCode())
            .like(StringUtils.isNotBlank(query.getCapabilityName()), AigCapability::getCapabilityName, query.getCapabilityName())
            .eq(StringUtils.isNotBlank(query.getDataPolicy()), AigCapability::getDataPolicy, query.getDataPolicy())
            .eq(StringUtils.isNotBlank(query.getAuditLevel()), AigCapability::getAuditLevel, query.getAuditLevel())
            .eq(StringUtils.isNotBlank(query.getStatus()), AigCapability::getStatus, query.getStatus())
            .orderByDesc(AigCapability::getCreateTime);
        Page<AigCapabilityVo> voPage = capabilityMapper.selectVoPage(pageQuery.build(), wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    public AigCapabilityVo getDetail(Long capabilityId) {
        if (capabilityId == null) {
            throw new ServiceException("能力ID不能为空");
        }
        AigCapabilityVo vo = capabilityMapper.selectVoById(capabilityId);
        if (vo == null) {
            throw new ServiceException("能力不存在");
        }
        return vo;
    }

    @Override
    public Long create(AigCapabilityBo bo) {
        String code = bo.getCapabilityCode() == null ? null : bo.getCapabilityCode().trim();
        if (existsCode(code, null)) {
            throw new ServiceException("能力编码已存在：" + code);
        }
        AigCapability entity = BeanUtil.copyProperties(bo, AigCapability.class);
        entity.setCapabilityId(null);
        entity.setCapabilityCode(code);
        entity.setStatus(StringUtils.isBlank(bo.getStatus()) ? STATUS_NORMAL : bo.getStatus());
        entity.setDataPolicy(StringUtils.isBlank(bo.getDataPolicy()) ? DEFAULT_DATA_POLICY : bo.getDataPolicy());
        entity.setAuditLevel(StringUtils.isBlank(bo.getAuditLevel()) ? DEFAULT_AUDIT_LEVEL : bo.getAuditLevel());
        capabilityMapper.insert(entity);
        return entity.getCapabilityId();
    }

    @Override
    public void update(AigCapabilityBo bo) {
        AigCapability exist = loadCapability(bo.getCapabilityId());
        String code = bo.getCapabilityCode() == null ? exist.getCapabilityCode() : bo.getCapabilityCode().trim();
        if (existsCode(code, bo.getCapabilityId())) {
            throw new ServiceException("能力编码已存在：" + code);
        }
        AigCapability entity = BeanUtil.copyProperties(bo, AigCapability.class);
        entity.setCapabilityId(exist.getCapabilityId());
        entity.setCapabilityCode(code);
        capabilityMapper.updateById(entity);
    }

    @Override
    public void remove(Long capabilityId) {
        loadCapability(capabilityId);
        capabilityMapper.deleteById(capabilityId);
    }

    /**
     * 加载能力，不存在时抛异常。
     *
     * @param capabilityId 能力ID
     * @return 能力实体
     */
    private AigCapability loadCapability(Long capabilityId) {
        if (capabilityId == null) {
            throw new ServiceException("能力ID不能为空");
        }
        AigCapability entity = capabilityMapper.selectById(capabilityId);
        if (entity == null) {
            throw new ServiceException("能力不存在");
        }
        return entity;
    }

    /**
     * 能力编码是否已被占用。
     *
     * @param code        能力编码
     * @param excludeId   需要排除的能力ID（编辑场景）
     * @return 是否已存在
     */
    private boolean existsCode(String code, Long excludeId) {
        if (StringUtils.isBlank(code)) {
            return false;
        }
        LambdaQueryWrapper<AigCapability> wrapper = new LambdaQueryWrapper<AigCapability>()
            .eq(AigCapability::getCapabilityCode, code)
            .ne(excludeId != null, AigCapability::getCapabilityId, excludeId);
        return capabilityMapper.selectCount(wrapper) > 0;
    }

}
