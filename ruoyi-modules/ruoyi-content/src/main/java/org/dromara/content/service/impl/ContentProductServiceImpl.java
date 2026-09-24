package org.dromara.content.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.CpProduct;
import org.dromara.content.domain.CpTask;
import org.dromara.content.domain.CpTaskFile;
import org.dromara.content.domain.bo.ContentProductBo;
import org.dromara.content.domain.vo.CpProductVo;
import org.dromara.content.enums.ContentFileKindEnum;
import org.dromara.content.enums.ContentFileSourceEnum;
import org.dromara.content.enums.ContentParseStatusEnum;
import org.dromara.content.helper.ContentOssHelper;
import org.dromara.content.mapper.CpProductMapper;
import org.dromara.content.mapper.CpTaskFileMapper;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.content.service.IContentProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

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

    /**
     * 任务 Mapper（校验附件归属、读任务的产品）
     */
    private final CpTaskMapper taskMapper;

    /**
     * 任务附件 Mapper
     */
    private final CpTaskFileMapper taskFileMapper;

    /**
     * 私有对象存储读取（产品图预览走后端代理）
     */
    private final ContentOssHelper ossHelper;

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
        vo.setProductImageConfigured(StringUtils.isNotBlank(vo.getProductImage()));
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

    // ------------------------------------------------------------------
    // 产品图（R4）
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CpProductVo bindImageFromFile(Long productId, Long fileId) {
        CpProduct product = load(productId);
        if (fileId == null) {
            throw new ServiceException("请指定要设为产品图的附件");
        }
        CpTaskFile file = taskFileMapper.selectById(fileId);
        if (file == null) {
            throw new ServiceException("附件不存在：" + fileId);
        }
        if (!ContentFileKindEnum.IMAGE.getCode().equalsIgnoreCase(file.getFileKind())) {
            throw new ServiceException("「" + StringUtils.blankToDefault(file.getFileName(), "未命名文件")
                + "」不是图片，产品图必须是图片附件");
        }
        if (StringUtils.isBlank(file.getFileRef())) {
            throw new ServiceException("附件还没有落对象存储，无法作为产品图：" + fileId);
        }
        CpTask task = taskMapper.selectById(file.getTaskId());
        if (task == null) {
            throw new ServiceException("附件所属任务已不存在：" + file.getTaskId());
        }
        if (task.getProductId() == null) {
            throw new ServiceException("任务「" + StringUtils.blankToDefault(task.getTaskName(), String.valueOf(task.getTaskId()))
                + "」没有关联产品，无法把附件设为产品图；请先在项目里选择产品");
        }
        if (!task.getProductId().equals(productId)) {
            throw new ServiceException("该附件属于其它产品（productId=" + task.getProductId()
                + "），不能设为当前产品（productId=" + productId + "）的产品图");
        }

        CpProduct update = new CpProduct();
        update.setProductId(productId);
        update.setProductImage(file.getFileRef());
        update.setProductImageTaskId(task.getTaskId());
        update.setProductImageSetAt(LocalDateTime.now());
        update.setProductImageSetBy(LoginHelper.getUserId());
        productMapper.updateById(update);

        // 角色标注：同一任务里以前被标过 PRODUCT 的其它附件降回 UPLOAD（一个任务只应有一张产品图，
        // 否则「本任务的产品图是哪张」又会变成歧义）
        demoteOtherProductFiles(task.getTaskId(), file.getFileId());
        markSource(file.getFileId(), ContentFileSourceEnum.PRODUCT);

        log.info("设定产品图, productId={}, fileId={}, taskId={}, ref={}",
            productId, file.getFileId(), task.getTaskId(), file.getFileRef());
        return getDetail(productId);
    }

    @Override
    public ProductImage imageOf(Long productId) {
        CpProduct product = load(productId);
        if (StringUtils.isBlank(product.getProductImage())) {
            return new ProductImage(false, null, null, null, null, null, null);
        }
        CpTaskFile source = findSourceFile(product);
        return new ProductImage(true,
            source == null ? null : source.getFileId(),
            source == null ? fileNameOfKey(product.getProductImage()) : source.getFileName(),
            source == null ? extOfKey(product.getProductImage()) : source.getFileExt(),
            product.getProductImageTaskId(),
            product.getProductImageSetAt(),
            product.getProductImageSetBy());
    }

    @Override
    public byte[] imageBytes(Long productId) {
        CpProduct product = load(productId);
        if (StringUtils.isBlank(product.getProductImage())) {
            throw new ServiceException("该产品还没有产品图：" + productId);
        }
        return ossHelper.getBytes(product.getProductImage());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long ensureProductAttachment(Long taskId) {
        if (taskId == null) {
            return null;
        }
        CpTask task = taskMapper.selectById(taskId);
        if (task == null || task.getProductId() == null) {
            return null;
        }
        CpProduct product = productMapper.selectById(task.getProductId());
        if (product == null || StringUtils.isBlank(product.getProductImage())) {
            return null;
        }
        // 已经有本产品图角色的登记就直接复用（幂等：重复调用不会堆附件）
        List<CpTaskFile> existing = taskFileMapper.selectList(new LambdaQueryWrapper<CpTaskFile>()
            .eq(CpTaskFile::getTaskId, taskId)
            .eq(CpTaskFile::getSourceType, ContentFileSourceEnum.PRODUCT.getCode())
            .eq(CpTaskFile::getFileRef, product.getProductImage())
            .orderByAsc(CpTaskFile::getFileId));
        if (!existing.isEmpty()) {
            return existing.get(0).getFileId();
        }

        CpTaskFile source = findSourceFile(product);
        CpTaskFile entity = new CpTaskFile();
        entity.setTaskId(taskId);
        entity.setFileName(source == null ? fileNameOfKey(product.getProductImage()) : source.getFileName());
        entity.setFileExt(source == null ? extOfKey(product.getProductImage()) : source.getFileExt());
        entity.setFileSize(source == null ? null : source.getFileSize());
        entity.setFileKind(ContentFileKindEnum.IMAGE.getCode());
        entity.setSourceType(ContentFileSourceEnum.PRODUCT.getCode());
        entity.setDataLevel(StringUtils.isBlank(task.getDataLevel()) ? "INTERNAL" : task.getDataLevel());
        // 图片不参与文本解析：直接记 SKIPPED，免得解析工作台把它当成一份待解析资料
        entity.setParseStatus(ContentParseStatusEnum.SKIPPED.getCode());
        entity.setParseMessage("产品图（对象复用自产品主数据，无需解析）");
        entity.setFileRef(product.getProductImage());
        taskFileMapper.insert(entity);
        log.info("任务补登记产品图附件, taskId={}, fileId={}, productId={}",
            taskId, entity.getFileId(), product.getProductId());
        return entity.getFileId();
    }

    /**
     * 找产品图的源附件（用于取文件名/扩展名/大小；找不到就按对象键推断）。
     *
     * @param product 产品
     * @return 源附件；找不到返回 null
     */
    private CpTaskFile findSourceFile(CpProduct product) {
        List<CpTaskFile> rows = taskFileMapper.selectList(new LambdaQueryWrapper<CpTaskFile>()
            .eq(product.getProductImageTaskId() != null, CpTaskFile::getTaskId, product.getProductImageTaskId())
            .eq(CpTaskFile::getFileRef, product.getProductImage())
            .orderByAsc(CpTaskFile::getFileId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 同一任务内除 keepFileId 外的 PRODUCT 角色附件降回 UPLOAD。
     *
     * @param taskId     任务ID
     * @param keepFileId 保留的产品图附件
     */
    private void demoteOtherProductFiles(Long taskId, Long keepFileId) {
        taskFileMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CpTaskFile>()
            .eq(CpTaskFile::getTaskId, taskId)
            .eq(CpTaskFile::getSourceType, ContentFileSourceEnum.PRODUCT.getCode())
            .ne(CpTaskFile::getFileId, keepFileId)
            .set(CpTaskFile::getSourceType, ContentFileSourceEnum.UPLOAD.getCode()));
    }

    /**
     * 标注附件来源角色。
     *
     * @param fileId 附件ID
     * @param source 角色
     */
    private void markSource(Long fileId, ContentFileSourceEnum source) {
        taskFileMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CpTaskFile>()
            .eq(CpTaskFile::getFileId, fileId)
            .set(CpTaskFile::getSourceType, source.getCode()));
    }

    /**
     * 从对象键推断文件名（形如 {@code content-private/{taskId}/{fileId}/original.jpg}）。
     *
     * @param key 对象键
     * @return 文件名
     */
    private static String fileNameOfKey(String key) {
        int idx = key == null ? -1 : key.lastIndexOf('/');
        String name = idx < 0 ? key : key.substring(idx + 1);
        return StringUtils.isBlank(name) ? "product-image" : name;
    }

    /**
     * 从对象键推断扩展名。
     *
     * @param key 对象键
     * @return 扩展名（不含点）；推断不出返回 null
     */
    private static String extOfKey(String key) {
        String name = fileNameOfKey(key);
        int idx = name.lastIndexOf('.');
        return idx < 0 ? null : name.substring(idx + 1).toLowerCase(Locale.ROOT);
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
