package org.dromara.aigov.workspace.portal.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.workspace.portal.domain.AigUserBrand;
import org.dromara.aigov.workspace.portal.domain.bo.AigUserBrandBo;
import org.dromara.aigov.workspace.portal.domain.vo.AigUserBrandVo;
import org.dromara.aigov.workspace.portal.mapper.AigUserBrandMapper;
import org.dromara.aigov.workspace.portal.service.IAigUserBrandService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户↔品牌归属管理实现（④）。
 *
 * <h3>为什么"撤销"是停用而不是删除</h3>
 * <p>唯一键是 {@code (user_id, brand_id)} 且**不含** {@code del_flag}：逻辑删除的行仍然占着这个键，
 * 于是"删掉再登记同一对"会撞唯一约束——而删除后的行被 {@code @TableLogic} 过滤掉，
 * 代码里也查不到它，报错会变成一句没法解释的"重复"。停用（{@code status='1'}）没有这个问题：
 * 重新登记时把同一行改回启用即可，且运维还能看到"这个人曾经属于这个品牌"。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigUserBrandServiceImpl implements IAigUserBrandService {

    /**
     * 记录状态：正常（参与可见性判定）
     */
    private static final String STATUS_NORMAL = "0";

    /**
     * 记录状态：停用（不参与判定，但保留痕迹）
     */
    private static final String STATUS_DISABLED = "1";

    private final AigUserBrandMapper userBrandMapper;

    @Override
    public List<AigUserBrandVo> listByUser(Long userId) {
        if (userId == null) {
            throw new ServiceException("用户ID不能为空");
        }
        return userBrandMapper.selectList(Wrappers.<AigUserBrand>lambdaQuery()
                .eq(AigUserBrand::getUserId, userId)
                .orderByDesc(AigUserBrand::getUserBrandId))
            .stream()
            .map(AigUserBrandServiceImpl::toVo)
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long grant(AigUserBrandBo bo) {
        Long userId = requirePositive(bo == null ? null : bo.getUserId(), "用户ID");
        Long brandId = requirePositive(bo.getBrandId(), "品牌ID");
        AigUserBrand existing = find(userId, brandId);
        if (existing != null) {
            if (!STATUS_NORMAL.equals(existing.getStatus())) {
                updateStatus(existing.getUserBrandId(), STATUS_NORMAL);
            }
            return existing.getUserBrandId();
        }
        AigUserBrand entity = new AigUserBrand();
        entity.setUserId(userId);
        entity.setBrandId(brandId);
        entity.setStatus(STATUS_NORMAL);
        entity.setRemark(bo.getRemark());
        try {
            userBrandMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发下另一个请求已插入同一对：以库里那条为准（唯一键是最终裁判）
            AigUserBrand winner = find(userId, brandId);
            if (winner == null) {
                throw e;
            }
            return winner.getUserBrandId();
        }
        return entity.getUserBrandId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long userBrandId) {
        if (userBrandId == null) {
            throw new ServiceException("归属记录ID不能为空");
        }
        AigUserBrand existing = userBrandMapper.selectById(userBrandId);
        if (existing == null) {
            throw new ServiceException("品牌归属不存在：" + userBrandId);
        }
        // 幂等：已经停用就不重复写（省一次更新，也让重复点击没有副作用）
        if (STATUS_DISABLED.equals(existing.getStatus())) {
            return;
        }
        updateStatus(userBrandId, STATUS_DISABLED);
    }

    /**
     * 按 user+brand 查一条（逻辑删除的行由 {@code @TableLogic} 自动过滤）。
     *
     * @param userId  用户ID
     * @param brandId 品牌ID
     * @return 记录；不存在返回 null
     */
    private AigUserBrand find(Long userId, Long brandId) {
        return userBrandMapper.selectOne(Wrappers.<AigUserBrand>lambdaQuery()
            .eq(AigUserBrand::getUserId, userId)
            .eq(AigUserBrand::getBrandId, brandId)
            .last("limit 1"));
    }

    /**
     * 只改状态。
     *
     * @param userBrandId 记录ID
     * @param status      目标状态
     */
    private void updateStatus(Long userBrandId, String status) {
        AigUserBrand update = new AigUserBrand();
        update.setUserBrandId(userBrandId);
        update.setStatus(status);
        userBrandMapper.updateById(update);
    }

    /**
     * 校验正整数ID。
     *
     * @param value 值
     * @param label 字段名（用于报错定位）
     * @return 值
     */
    private static Long requirePositive(Long value, String label) {
        if (value == null || value <= 0) {
            throw new ServiceException(label + "不合法：" + value);
        }
        return value;
    }

    /**
     * 实体 → 视图。
     *
     * @param entity 实体
     * @return 视图
     */
    private static AigUserBrandVo toVo(AigUserBrand entity) {
        AigUserBrandVo vo = new AigUserBrandVo();
        vo.setUserBrandId(entity.getUserBrandId());
        vo.setUserId(entity.getUserId());
        vo.setBrandId(entity.getBrandId());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

}
