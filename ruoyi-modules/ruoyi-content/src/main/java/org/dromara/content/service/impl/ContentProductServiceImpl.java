package org.dromara.content.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.CpProduct;
import org.dromara.content.domain.bo.ContentProductBo;
import org.dromara.content.domain.vo.CpProductVo;
import org.dromara.content.mapper.CpProductMapper;
import org.dromara.content.service.IContentProductService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 轻量产品/SKU服务实现。
 *
 * <p>产品编码 + SKU 编码在库上有唯一索引（{@code uk_cp_product_code}），
 * 这里在建/改之前先做一次可读性更好的校验，避免把主键冲突直接抛给用户。</p>
 *
 * @author content
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentProductServiceImpl implements IContentProductService {

    /**
     * 状态：正常
     */
    private static final String STATUS_NORMAL = "0";

    /**
     * 产品 Mapper
     */
    private final CpProductMapper productMapper;

    @Override
    public PageResult<CpProductVo> queryPage(ContentProductBo bo, PageQuery pageQuery) {
        ContentProductBo query = bo == null ? new ContentProductBo() : bo;
        LambdaQueryWrapper<CpProduct> wrapper = new LambdaQueryWrapper<CpProduct>()
            .and(StringUtils.isNotBlank(query.getKeyword()), w -> w
                .like(CpProduct::getProductCode, query.getKeyword())
                .or().like(CpProduct::getProductName, query.getKeyword())
                .or().like(CpProduct::getSkuCode, query.getKeyword())
                .or().like(CpProduct::getSkuName, query.getKeyword()))
            .eq(StringUtils.isNotBlank(query.getStatus()), CpProduct::getStatus, query.getStatus())
            .orderByDesc(CpProduct::getCreateTime);
        var voPage = productMapper.selectVoPage(pageQuery.build(), wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    public List<CpProductVo> options() {
        return productMapper.selectVoList(new LambdaQueryWrapper<CpProduct>()
            .eq(CpProduct::getStatus, STATUS_NORMAL)
            .orderByAsc(CpProduct::getProductName));
    }

    @Override
    public CpProductVo getDetail(Long productId) {
        CpProductVo vo = productMapper.selectVoById(load(productId).getProductId());
        if (vo == null) {
            throw new ServiceException("产品不存在");
        }
        return vo;
    }

    @Override
    public Long create(ContentProductBo bo) {
        String code = trim(bo.getProductCode());
        if (exists(code, trim(bo.getSkuCode()), null)) {
            throw new ServiceException("产品编码与SKU组合已存在：" + code);
        }
        CpProduct entity = BeanUtil.copyProperties(bo, CpProduct.class);
        entity.setProductId(null);
        entity.setProductCode(code);
        entity.setSkuCode(trim(bo.getSkuCode()));
        entity.setStatus(StringUtils.isBlank(bo.getStatus()) ? STATUS_NORMAL : bo.getStatus());
        productMapper.insert(entity);
        log.info("新增产品完成, productId={}, code={}", entity.getProductId(), code);
        return entity.getProductId();
    }

    @Override
    public void update(ContentProductBo bo) {
        CpProduct exist = load(bo.getProductId());
        String code = StringUtils.isBlank(bo.getProductCode()) ? exist.getProductCode() : trim(bo.getProductCode());
        String sku = bo.getSkuCode() == null ? exist.getSkuCode() : trim(bo.getSkuCode());
        if (exists(code, sku, exist.getProductId())) {
            throw new ServiceException("产品编码与SKU组合已存在：" + code);
        }
        CpProduct entity = BeanUtil.copyProperties(bo, CpProduct.class);
        entity.setProductId(exist.getProductId());
        entity.setProductCode(code);
        entity.setSkuCode(sku);
        productMapper.updateById(entity);
    }

    @Override
    public void remove(Long productId) {
        load(productId);
        productMapper.deleteById(productId);
    }

    /**
     * 加载产品，不存在抛异常。
     *
     * @param productId 产品ID
     * @return 产品实体
     */
    private CpProduct load(Long productId) {
        if (productId == null) {
            throw new ServiceException("产品ID不能为空");
        }
        CpProduct entity = productMapper.selectById(productId);
        if (entity == null) {
            throw new ServiceException("产品不存在");
        }
        return entity;
    }

    /**
     * 判断产品编码 + SKU 是否已存在。
     *
     * @param productCode 产品编码
     * @param skuCode     SKU编码（可空）
     * @param excludeId   排除的产品ID
     * @return 是否已存在
     */
    private boolean exists(String productCode, String skuCode, Long excludeId) {
        if (StringUtils.isBlank(productCode)) {
            return false;
        }
        LambdaQueryWrapper<CpProduct> wrapper = new LambdaQueryWrapper<CpProduct>()
            .eq(CpProduct::getProductCode, productCode)
            .eq(CpProduct::getSkuCode, skuCode)
            .ne(excludeId != null, CpProduct::getProductId, excludeId);
        return productMapper.selectCount(wrapper) > 0;
    }

    /**
     * 去空格，空串归 null。
     *
     * @param text 文本
     * @return 归一文本
     */
    private String trim(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        return t.isEmpty() ? null : t;
    }

}
