package org.dromara.hrtalent.service.recruitment;

import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitImportBatchVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitImportPreviewVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitImportResultVo;
import org.dromara.hrtalent.enums.RecruitImportTypeEnum;
import org.springframework.web.multipart.MultipartFile;

/**
 * 招聘数据导入服务（两段式：预检 → 确认）。
 *
 * <p><b>为什么必须两段式</b>：导入的失败模式不是「传不上去」，而是「传上去了、一半行不合法、
 * 数据已经进库了」。预检先把每一行的问题按行号摊开，人确认后才真正落库；
 * 期间源文件存对象存储，批次与逐行错误都留痕，事后能回答「哪次导入、哪个文件、谁操作的」。</p>
 *
 * @author hr-talent
 */
public interface IRecruitImportService {

    /**
     * 输出导入模板（Excel）。
     *
     * @param type     导入类型
     * @param response HTTP 响应
     */
    void writeTemplate(RecruitImportTypeEnum type, HttpServletResponse response);

    /**
     * 上传并预检：解析文件、逐行校验、登记批次与错误明细，<b>不写业务数据</b>。
     *
     * @param type 导入类型
     * @param file 上传文件
     * @return 预检结果（含批次ID）
     */
    RecruitImportPreviewVo preview(RecruitImportTypeEnum type, MultipartFile file);

    /**
     * 确认导入：取回源文件重放，导入其中合法的行。
     *
     * @param batchId 批次ID
     * @return 导入结果
     */
    RecruitImportResultVo confirm(Long batchId);

    /**
     * 取消批次（仅「待确认」可取消，不产生任何业务数据）。
     *
     * @param batchId 批次ID
     */
    void cancel(Long batchId);

    /**
     * 批次详情。
     *
     * @param batchId 批次ID
     * @return 批次
     */
    RecruitImportBatchVo getBatch(Long batchId);

    /**
     * 批次分页（可按导入类型过滤）。
     *
     * @param importType 导入类型（可空）
     * @param pageQuery  分页参数
     * @return 批次分页
     */
    PageResult<RecruitImportBatchVo> queryBatchPage(String importType, PageQuery pageQuery);

}
