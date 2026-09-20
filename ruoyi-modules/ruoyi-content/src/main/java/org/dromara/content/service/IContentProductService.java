package org.dromara.content.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.bo.ContentProductBo;
import org.dromara.content.domain.vo.CpProductVo;

import java.util.List;

/**
 * 轻量产品/SKU服务。
 *
 * @author content
 */
public interface IContentProductService {

    /**
     * 分页查询。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<CpProductVo> queryPage(ContentProductBo bo, PageQuery pageQuery);

    /**
     * 下拉选项（仅启用的产品）。
     *
     * @return 产品列表
     */
    List<CpProductVo> options();

    /**
     * 详情。
     *
     * @param productId 产品ID
     * @return 产品
     */
    CpProductVo getDetail(Long productId);

    /**
     * 新增。
     *
     * @param bo 产品参数
     * @return 产品ID
     */
    Long create(ContentProductBo bo);

    /**
     * 修改。
     *
     * @param bo 产品参数
     */
    void update(ContentProductBo bo);

    /**
     * 删除。
     *
     * @param productId 产品ID
     */
    void remove(Long productId);

}
