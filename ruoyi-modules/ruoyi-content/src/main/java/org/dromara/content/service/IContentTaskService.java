package org.dromara.content.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.bo.ContentTaskBo;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.dromara.content.domain.vo.CpTaskVo;
import org.dromara.content.domain.vo.ContentTaskDetailVo;
import org.dromara.content.domain.vo.ContentFactSyncVo;
import org.dromara.content.helper.ContentGateEngine;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 内容生产任务服务。
 *
 * @author content
 */
public interface IContentTaskService {

    /**
     * 分页查询任务。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<CpTaskVo> queryPage(ContentTaskBo bo, PageQuery pageQuery);

    /**
     * 任务详情（含附件、事实、卡片、作业、闸门结论）。
     *
     * @param taskId 任务ID
     * @return 详情
     */
    ContentTaskDetailVo getDetail(Long taskId);

    /**
     * 轻量校验任务存在（只需"这个任务在不在"时用它，不要为了校验去拉整个详情）。
     *
     * <p>给品牌 Brief 这类挂在任务上的附属数据用：保存/读取前确认任务存在，
     * 否则会出现"给不存在的任务写了一条要求"这种查不出来的脏数据。</p>
     *
     * @param taskId 任务ID
     */
    void requireTask(Long taskId);

    /**
     * 新建任务。
     *
     * @param bo 任务参数
     * @return 任务ID
     */
    Long create(ContentTaskBo bo);

    /**
     * 修改任务。
     *
     * @param bo 任务参数
     */
    void update(ContentTaskBo bo);

    /**
     * 删除任务（逻辑删除）。
     *
     * @param taskId 任务ID
     */
    void remove(Long taskId);

    /**
     * 上传资料附件（来源角色固定为 {@code UPLOAD}）。
     *
     * @param taskId 任务ID
     * @param dataLevel 该文件的数据等级（可空，取任务等级）
     * @param file   文件
     * @return 附件ID
     */
    Long uploadFile(Long taskId, String dataLevel, MultipartFile file);

    /**
     * 上传附件并**显式声明来源角色**（{@code cp_task_file.source_type}）。
     *
     * <p><b>为什么要这个重载</b>：产品照片、被引用的参考图、系统生成的结果图都会落成
     * {@code file_kind=IMAGE}，只靠 file_kind 分不出「这张是风格参考、还是刚生成的候选」，
     * 页面角色标签与出图选图都受影响（内测 S15）。</p>
     *
     * <p><b>为什么由调用方传、而不是服务端推断</b>：与 {@link org.dromara.content.enums.ContentFileSourceEnum}
     * 的口径保持一致——系统不「推测」角色。但**调用方自己知道自己传的是什么**
     * （视觉工厂传参考图 / 出图产出 / 精修终版），那是声明，不是猜测。</p>
     *
     * @param taskId     任务ID
     * @param dataLevel  该文件的数据等级（可空，取任务等级）
     * @param file       文件
     * @param sourceType 来源角色编码（可空＝{@code UPLOAD}）；非法编码直接拒绝
     * @return 附件ID
     */
    Long uploadFile(Long taskId, String dataLevel, MultipartFile file, String sourceType);

    /**
     * 附件列表。
     *
     * @param taskId 任务ID
     * @return 附件列表
     */
    List<CpTaskFileVo> listFiles(Long taskId);

    /**
     * 读取任务附件的原始字节（页面预览用）。
     *
     * <p><b>为什么必须走这个服务端接口</b>：内容附件落在对象存储的私有前缀里、且不登记
     * {@code sys_oss}，前端拿不到可用的直链（见 {@code ContentOssHelper} 的安全约定）。
     * 之前内容模块只有「上传/列表」而没有「读内容」，所以「内容检查」里选参考图时
     * 只能显示文件名——用户看到的是一个像文本的下拉框，而不是图片。</p>
     *
     * <p>只放行图片附件：这个接口是给「看图选图」用的，把任意文档内联返回没有业务需要，
     * 反而多一个把非图片内容当图片渲染的风险面。</p>
     *
     * @param taskId 任务ID
     * @param fileId 附件ID
     * @return 图片字节 + 文件名 + MIME
     */
    FileContent readFileContent(Long taskId, Long fileId);

    /**
     * 附件内容读取结果。
     *
     * @param bytes    字节
     * @param fileName 原始文件名（带扩展名）
     * @param mimeType 图片 MIME
     */
    record FileContent(byte[] bytes, String fileName, String mimeType) {
    }

    /**
     * 触发解析（异步）。
     *
     * @param taskId 任务ID
     * @return 作业ID
     */
    Long triggerParse(Long taskId);

    /**
     * 触发预检（异步）。
     *
     * @param taskId 任务ID
     * @return 作业ID
     */
    Long triggerPrecheck(Long taskId);

    /**
     * 重算闸门并刷新任务状态。
     *
     * @param taskId 任务ID
     * @return 判定结论
     */
    ContentGateEngine.GateResult recheck(Long taskId);

    /**
     * 把任务所选产品在「产品与SKU」模块里的主数据同步为产品事实。
     *
     * <p>解决的实际问题：用户在新增任务时选中了产品，但闸门只认 {@code cp_fact_snapshot}
     * 里已确认的事实——产品主数据里有名称和 SKU 也不算数，于是「选了产品还要再录一遍」。</p>
     *
     * <p><b>边界（重要）</b>：只同步产品与SKU模块<b>确实拥有</b>的两个字段
     * （{@code product_name}、{@code sku_code}）。主体版本/颜色/数量/参数/包装版本
     * 在该模块里本就没有对应列，只能来自产品资料（解析+确认）或人工录入——
     * 这也守住了「产品事实只能来自经确认的产品资料」的红线。</p>
     *
     * <p><b>不覆盖人的判断</b>：若同字段已存在<b>不同取值</b>，本次写入降级为「待确认」，
     * 交给事实清单/互动卡由人裁定，绝不自动把主数据值确认成事实。</p>
     *
     * @param taskId 任务ID
     * @return 同步结果（写入/跳过/冲突条数与逐条说明）
     */
    ContentFactSyncVo syncProductFacts(Long taskId);

}
