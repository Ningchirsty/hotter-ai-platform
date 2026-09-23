package org.dromara.creative.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.bo.ContentTaskBo;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.dromara.creative.domain.bo.CreativeProjectBo;
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
     * 项目的附件列表（含参考图）。
     *
     * @param taskId 项目ID
     * @return 附件列表
     */
    List<CpTaskFileVo> listFiles(Long taskId);

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
