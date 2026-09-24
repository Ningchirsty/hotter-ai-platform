package org.dromara.content.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.bo.ContentOutputCheckQueryBo;
import org.dromara.content.domain.vo.CpOutputCheckVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 成品一致性检查服务接口。
 *
 * <p><b>能力边界</b>：本服务回答的是「出稿的成品图与原参考图是否一致」，
 * 属于<b>验收</b>动作。它<b>不产生、也不修改任何产品事实</b>——参考图与 AI 结论
 * 都不得反向成为产品结构、颜色、数量、包装、参数的依据（SPEC §0.1 红线第 2 条）。</p>
 *
 * @author content
 */
public interface IContentOutputCheckService {

    /**
     * 检查记录分页。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<CpOutputCheckVo> queryPage(ContentOutputCheckQueryBo bo, PageQuery pageQuery);

    /**
     * 检查详情。
     *
     * @param checkId 检查ID
     * @return 详情
     */
    CpOutputCheckVo getDetail(Long checkId);

    /**
     * 某任务下的检查记录（倒序）。
     *
     * @param taskId 任务ID
     * @return 检查列表
     */
    List<CpOutputCheckVo> listByTask(Long taskId);

    /**
     * 发起一次成品一致性检查（异步执行比对）。
     *
     * <p>参考图有两种给法：引用任务里已有的图片附件（{@code referenceFileId}），
     * 或直接上传（{@code referenceFile}）。二者至少给一个；都给时以已存在的附件为准。</p>
     *
     * @param taskId          任务ID
     * @param referenceFileId 已有参考图附件ID（可空）
     * @param referenceFile   新上传的参考图（可空）
     * @param resultFile      成品图（必填）
     * @param remark          备注（可空）
     * @return 检查ID
     */
    Long run(Long taskId, Long referenceFileId, MultipartFile referenceFile,
             MultipartFile resultFile, String remark);

    /**
     * 用两个<b>已有附件</b>发起一次检查（不重复登记附件）。
     *
     * <p>为什么需要它：R4 起同一张生成图要跑两次比对——一次以「参考图」为基准（风格改动多少），
     * 一次以「产品图」为基准（产品还是不是那个产品）。若第二次再走 {@link #run} 上传一次，
     * 同一张生成图会在附件表里躺两条记录，「这张图是哪一次检查的成品」立刻说不清。
     * 本方法只复用已有附件，结论各自独立留痕。</p>
     *
     * @param taskId          任务ID
     * @param referenceFileId 参考图附件ID（必填，须属于该任务）
     * @param resultFileId    成品图附件ID（必填，须属于该任务）
     * @param remark          备注（可空，建议写明基准是哪张图）
     * @return 检查ID
     */
    Long runWithFiles(Long taskId, Long referenceFileId, Long resultFileId, String remark);

    /**
     * 删除检查记录（逻辑删除；附件与对象存储保留，便于追溯）。
     *
     * @param checkId 检查ID
     */
    void remove(Long checkId);

    /**
     * 读取检查涉及的图片字节（用于页面预览，避免暴露对象存储直链）。
     *
     * @param checkId 检查ID
     * @param side    {@code reference} 或 {@code result}
     * @return 图片字节
     */
    CheckImage loadImage(Long checkId, String side);

    /**
     * 预览图片及其元信息。
     *
     * @param bytes    图片字节
     * @param fileName 文件名
     * @param mimeType MIME 类型
     */
    record CheckImage(byte[] bytes, String fileName, String mimeType) {
    }

}
