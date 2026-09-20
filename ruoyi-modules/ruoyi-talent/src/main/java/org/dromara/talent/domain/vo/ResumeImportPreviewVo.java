package org.dromara.talent.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 简历导入预览结果视图对象。
 * <p>
 * 只回传抽取到的候选字段、来源与置信度，<b>不回传简历正文</b>；
 * {@code importToken} 仅用于确认时定位服务端临时文件，不落数据库。
 *
 * @author talent
 */
@Data
public class ResumeImportPreviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 导入凭证（不可猜 UUID，仅用于定位临时文件）
     */
    private String importToken;

    /**
     * 原始文件名（已做 URL 解码容错）
     */
    private String originalName;

    /**
     * 扩展名（小写，无点）
     */
    private String ext;

    /**
     * 文件字节数
     */
    private Long size;

    /**
     * 是否成功提取到文本（jpg/jpeg/png 恒为 false）
     */
    private Boolean textExtracted;

    /**
     * 提取到的文本长度（按 talent.import-max-text-length 截断后）
     */
    private Integer textLength;

    /**
     * 候选字段清单
     */
    private List<ResumeFieldCandidateVo> candidates = new ArrayList<>();

    /**
     * 提示信息（图片型简历、未提取到文本、仅识别到年份等）
     */
    private List<String> warnings = new ArrayList<>();

}
