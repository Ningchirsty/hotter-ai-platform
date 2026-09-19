package org.dromara.talent.service;

import org.dromara.talent.domain.bo.ResumeImportConfirmBo;
import org.dromara.talent.domain.vo.ResumeImportPreviewVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 导入简历直接建档服务。
 * <p>
 * 全流程在服务端进程内完成（PDFBox / POI / 正则），<b>不调用任何外部 AI / OCR / HTTP 服务</b>；
 * 抽取结果只作为候选值下发，必须人工确认后才建档。
 *
 * @author talent
 */
public interface ITalentResumeImportService {

    /**
     * 上传预览：校验开关 / 扩展名 / 大小，写临时文件并本地抽取候选字段。
     *
     * @param file 简历文件
     * @return 预览结果（含 importToken 与候选字段）
     */
    ResumeImportPreviewVo preview(MultipartFile file);

    /**
     * 确认建档：复用新增路径完成校验与建档，落附件、写解析留痕、清理临时文件。
     *
     * @param bo 确认入参（importToken + 人才主档信息）
     * @return [talentId, attachmentId]
     */
    Long[] confirm(ResumeImportConfirmBo bo);

}
