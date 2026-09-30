package org.dromara.creative.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.bo.ContentTaskBo;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.dromara.creative.domain.bo.CreativeProjectBo;
import org.dromara.creative.domain.vo.ProjectMaterialsVo;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.domain.vo.DpStageEventVo;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 视觉项目服务。
 *
 * <p>项目本体是内容协同的 {@code cp_task}（决策②），本服务是它在视觉工厂里的视图与阶段推进器：
 * 建项目、传参考图、读阶段、写阶段事件。内容侧的资料解析、事实确认、闸门、开工包一律复用
 * {@code IContentTaskService}，不重复实现。</p>
 *
 * @author creative
 */
public interface ICreativeProjectService {

    /**
     * 分页查询视觉项目（交付类型固定为 ECOM_DETAIL）。
     *
     * @param bo        查询条件（交付类型由服务端强制覆盖）
     * @param pageQuery 分页参数
     * @return 分页结果（含视觉阶段）
     */
    PageResult<CreativeProjectVo> queryPage(ContentTaskBo bo, PageQuery pageQuery);

    /**
     * 项目详情（含视觉阶段、参考图数、候选数）。
     *
     * @param taskId 项目ID
     * @return 项目详情
     */
    CreativeProjectVo getProject(Long taskId);

    /**
     * 新建视觉项目（= 新建一个 ECOM_DETAIL 的内容协同任务，并初始化视觉阶段）。
     *
     * @param bo 项目参数
     * @return 项目ID
     */
    Long createProject(CreativeProjectBo bo);

    /**
     * 上传参考图（产品图）到项目附件。
     *
     * @param taskId 项目ID
     * @param file   图片文件
     * @return 附件ID（cp_task_file.file_id）
     */
    Long uploadReference(Long taskId, MultipartFile file);

    /**
     * 上传参考图，并可选地同时把它设为该产品在产品主数据里的产品图。
     *
     * <p><b>为什么要有这个开关</b>：业务上传的那张图往往就是产品照片本身。
     * 如果不在上传时顺手登记，产品主数据会一直是空的，后面的「以产品图为保真基准」
     * 就永远无从谈起。</p>
     *
     * @param taskId         项目ID
     * @param file           图片文件
     * @param asProductImage 是否同时设为该产品的产品图（会写回 cp_product.product_image）
     * @return 附件ID（cp_task_file.file_id）
     */
    Long uploadReference(Long taskId, MultipartFile file, boolean asProductImage);

    // ------------------------------------------------------------------
    // 产品图（R4）
    // ------------------------------------------------------------------

    /**
     * 读取项目所属产品的产品图信息。
     *
     * @param taskId 项目ID
     * @return 产品图视图（含来源任务、是否已配置、可用的预览地址）
     */
    ProductImageView productImage(Long taskId);

    /**
     * 读取项目所属产品的产品图字节（后端代理预览）。
     *
     * @param taskId 项目ID
     * @return 字节 + MIME + 文件名
     */
    FileContent productImageContent(Long taskId);

    /**
     * 把项目里的某个附件设为该产品的产品图。
     *
     * @param taskId 项目ID
     * @param fileId 附件ID
     * @return 设定后的产品图视图
     */
    ProductImageView bindProductImage(Long taskId, Long fileId);

    /**
     * 产品图视图。
     *
     * @param productId   产品ID
     * @param productName 产品名
     * @param configured  该产品是否已有产品图
     * @param fileId      本项目里产品图角色的附件ID（可能为空：产品图来自别的项目）
     * @param fileName    文件名
     * @param sourceTaskId 产品图来源任务（若来自别的项目，用于如实标注）
     * @param setAt       设定时间
     * @param setBy       设定人
     * @param previewUrl  预览地址（后端代理；未配置时为 null）
     * @param note        可读说明（未配置时告诉人怎么补）
     */
    record ProductImageView(Long productId, String productName, boolean configured, Long fileId,
                            String fileName, Long sourceTaskId, java.time.LocalDateTime setAt,
                            Long setBy, String previewUrl, String note) {
    }

    /**
     * 项目的附件列表（含参考图）。
     *
     * @param taskId 项目ID
     * @return 附件列表
     */
    List<CpTaskFileVo> listFiles(Long taskId);

    /**
     * 附件缩略图（V0.2 R24）：把原图按最长边缩到 320px 的 JPEG。
     *
     * <p><b>为什么需要它</b>：资产抽屉此前直接读原图——实测一张 6.7MB 的附件首次 3.9s、
     * 之后每次仍要 1.2~1.8s（接口没有缓存头，等于每次打开抽屉都重新下载几 MB）。
     * 缩略图把单张压到几十 KB，并在进程内按「附件ID+原图大小」缓存字节，
     * 第二次起连对象存储都不用读。</p>
     *
     * @param taskId 项目ID
     * @param fileId 附件ID
     * @return 缩略图字节与内容类型
     */
    FileContent readFileThumbnail(Long taskId, Long fileId);

    /**
     * 项目素材概况（V0.2 R25）：将删除多少附件/生成记录/版本、共多少字节。
     *
     * <p>只读：不删任何东西。它是"显式清理素材"的第一步——让人在按下删除之前先看清代价。</p>
     *
     * @param taskId 项目ID
     * @return 素材概况
     */
    ProjectMaterialsVo materials(Long taskId);

    /**
     * 显式清理项目素材（V0.2 R25，按用户决定）：删对象存储里的文件 + 附件行 + 生成记录。
     *
     * <p><b>默认不清理</b>：删项目只软删项目本身，素材一律保留（见 R24 记录 §5.4 的结论）。
     * 释放空间只能通过这个显式动作，且要过三道闸：</p>
     * <ol>
     *   <li>{@code confirmName} 必须与项目名**逐字相同**（防手滑）；</li>
     *   <li>项目**还没被删除**时必须显式 {@code force=true}（正在用的项目清素材等于毁掉它）；</li>
     *   <li>先删对象、再删库行（顺序反了会留下孤儿对象，反过来则会留下断链）。</li>
     * </ol>
     *
     * <p>不删的东西也写清楚：分镜、文案块、模块计划、基因、阶段事件都不动——
     * 它们是创作成果与审计痕迹，不是"素材"。所以清理后页面仍能看到当时怎么做的。</p>
     *
     * @param taskId      项目ID
     * @param confirmName 二次确认：项目名
     * @param force       项目未删除时是否强制
     * @return 清理结果
     */
    ProjectMaterialsVo purgeMaterials(Long taskId, String confirmName, boolean force);

    /**
     * 读取项目附件的字节内容（参考图预览走后端代理）。
     *
     * <p>对象存储是私有桶、对浏览器不可达，内容模块也没有提供附件内容通道，
     * 因此视觉工厂自己开一个鉴权代理——不引入任何公网可达的图片地址。</p>
     *
     * @param taskId 项目ID
     * @param fileId 附件ID
     * @return 字节 + MIME + 文件名
     */
    FileContent readFileContent(Long taskId, Long fileId);

    /**
     * 附件内容。
     *
     * @param bytes       字节
     * @param contentType MIME
     * @param fileName    原始文件名
     */
    record FileContent(byte[] bytes, String contentType, String fileName) {
    }

    /**
     * 阶段事件时间线（全链路可追溯）。
     *
     * @param taskId 项目ID
     * @return 事件列表（按时间正序）
     */
    List<DpStageEventVo> timeline(Long taskId);

    /**
     * 读取项目当前视觉阶段；未初始化时返回 {@link DpVisualStageEnum#MATERIAL_READY}。
     *
     * @param taskId 项目ID
     * @return 阶段编码
     */
    String stageOf(Long taskId);

    /**
     * 推进视觉阶段（唯一写入口，带合法性校验与事件留痕）。
     *
     * @param taskId     项目ID
     * @param target     目标阶段
     * @param action     动作编码（如 HERO_SUBMIT）
     * @param detailJson 事件明细（可空）
     */
    void moveStage(Long taskId, DpVisualStageEnum target, String action, String detailJson);

    /**
     * 记一条阶段事件（不改阶段时用，例如上传参考图）。
     *
     * @param taskId     项目ID
     * @param eventType  事件类型
     * @param action     动作编码
     * @param detailJson 事件明细（可空）
     */
    void appendEvent(Long taskId, String eventType, String action, String detailJson);

}
