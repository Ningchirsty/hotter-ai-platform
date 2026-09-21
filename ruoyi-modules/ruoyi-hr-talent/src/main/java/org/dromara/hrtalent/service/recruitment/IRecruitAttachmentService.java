package org.dromara.hrtalent.service.recruitment;

import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.recruitment.AttachmentDownloadBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitAttachmentBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitAttachmentQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitAttachmentVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 招聘业务附件服务（SPEC-P3 §3.5 / 设计文档 §8.8、§9.4、§15.3）。
 *
 * <p><b>硬约束</b>：</p>
 * <ul>
 *     <li>对象存储只保存对象标识，不保存长期公共下载地址；</li>
 *     <li>同一业务对象同一附件类型的新文件新增版本，旧版本当前标识置否，不覆盖旧文件；</li>
 *     <li>预览与下载必须先做业务记录资源级鉴权，并写入敏感操作审计；用途为空一律拒绝；</li>
 *     <li>下载走鉴权后的<b>流式响应</b>，不返回长期匿名地址（§11.1）；</li>
 *     <li>删除一律逻辑删除，不物理删除数据库行，也不删除对象存储文件（§6、§15.3）。</li>
 * </ul>
 *
 * @author hr-talent
 */
public interface IRecruitAttachmentService {

    /**
     * 上传附件：校验扩展名/MIME/大小/数量后写入对象存储，并登记带版本号的业务记录。
     *
     * @param bo   业务定位与附件元数据入参
     * @param file 上传文件
     * @return 新增的附件ID
     */
    Long upload(RecruitAttachmentBo bo, MultipartFile file);

    /**
     * 分页查询某业务对象的附件列表。
     *
     * @param bo        查询条件（业务类型与业务对象ID必填）
     * @param pageQuery 分页参数
     * @return 附件分页结果
     */
    PageResult<RecruitAttachmentVo> queryPage(RecruitAttachmentQueryBo bo, PageQuery pageQuery);

    /**
     * 鉴权并审计后流式预览附件（{@code Content-Disposition: inline}）。
     *
     * @param attachmentId 附件ID
     * @param bo           用途入参（用途为空拒绝并写 denied 审计）
     * @param response     HTTP 响应
     */
    void preview(Long attachmentId, AttachmentDownloadBo bo, HttpServletResponse response);

    /**
     * 鉴权并审计后流式下载附件（{@code Content-Disposition: attachment}）。
     *
     * @param attachmentId 附件ID
     * @param bo           用途入参（用途为空拒绝并写 denied 审计）
     * @param response     HTTP 响应
     */
    void download(Long attachmentId, AttachmentDownloadBo bo, HttpServletResponse response);

    /**
     * 逻辑删除附件（不物理删除数据行，也不删除对象存储文件）。
     *
     * @param attachmentIds 附件ID数组
     */
    void remove(Long[] attachmentIds);

}
