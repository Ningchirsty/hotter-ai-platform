package org.dromara.content.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.bo.ContentProductBo;
import org.dromara.content.domain.vo.CpProductVo;

import java.time.LocalDateTime;
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

    // ------------------------------------------------------------------
    // 产品图（R4）
    // ------------------------------------------------------------------

    /**
     * 把某个任务附件设为该产品的产品图。
     *
     * <p><b>为什么走附件而不是让业务重新上传</b>：生产实测里业务已经在项目里上传了产品照片，
     * 再传一次既多余又会出现「到底哪张才算」的歧义。这里把那张图提升为产品主数据的一部分，
     * 同时把附件的来源角色标成 {@code PRODUCT}，并把来源任务/时间/人一起留痕。</p>
     *
     * <p>校验是硬的：附件必须存在、必须是图片、其所属任务的产品必须就是 {@code productId}。
     * 不满足一律可读报错，不做「猜你要设哪张」的兜底。</p>
     *
     * @param productId 产品ID
     * @param fileId    附件ID（cp_task_file.file_id）
     * @return 更新后的产品
     */
    CpProductVo bindImageFromFile(Long productId, Long fileId);

    /**
     * 读取产品的产品图信息（不读字节）。
     *
     * @param productId 产品ID
     * @return 产品图信息；未配置时 {@link ProductImage#configured()} 为 false
     */
    ProductImage imageOf(Long productId);

    /**
     * 读取产品图字节（供后端代理预览；对象存储是私有桶，前端不能直连）。
     *
     * @param productId 产品ID
     * @return 图片字节
     */
    byte[] imageBytes(Long productId);

    /**
     * 确保任务里存在一张「产品图」角色的附件，返回其附件ID。
     *
     * <p>用途：出图与质检都要以产品图为基准，而内容侧的成品一致性检查只接受「属于本任务的附件」。
     * 产品图对象不复制（同一对象键），只在任务里补一条 {@code source_type=PRODUCT} 的登记，
     * 于是「本任务的产品图是哪张」在附件列表里直接可见，也不再需要放宽检查服务的归属校验。</p>
     *
     * @param taskId 任务ID
     * @return 产品图附件ID；任务无产品、或产品未配置产品图时返回 null
     */
    Long ensureProductAttachment(Long taskId);

    /**
     * 产品图信息。
     *
     * @param configured   是否已配置
     * @param fileId       本任务里产品图角色的附件ID（若已登记）
     * @param fileName     文件名
     * @param fileExt      扩展名
     * @param sourceTaskId 产品图来源任务
     * @param setAt        设定时间
     * @param setBy        设定人
     */
    record ProductImage(boolean configured, Long fileId, String fileName, String fileExt,
                        Long sourceTaskId, LocalDateTime setAt, Long setBy) {
    }

}
