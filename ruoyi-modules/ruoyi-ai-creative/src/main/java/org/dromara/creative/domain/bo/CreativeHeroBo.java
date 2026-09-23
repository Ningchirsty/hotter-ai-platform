package org.dromara.creative.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * HERO 主图出图请求。
 *
 * <p>R0 的最小闭环就靠它：一张产品/参考图 + 一段提示词 → 一个候选。
 * 提示词可留空，服务端会用 {@code buildDefaultPrompt} 生成一段保守的主图提示词，
 * 保证「什么都不填也能出一张可看的图」，而不是报错让人先去学提示词工程。</p>
 *
 * @author creative
 */
@Data
public class CreativeHeroBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 参考图附件ID（cp_task_file.file_id）；留空则自动取该项目最近一张图片附件
     */
    private Long fileId;

    /**
     * 正向提示词（留空用默认模板）
     */
    @Size(max = 1000, message = "提示词长度不能超过 1000")
    private String prompt;

    /**
     * 负向提示词
     */
    @Size(max = 500, message = "负向提示词长度不能超过 500")
    private String negativePrompt;

    /**
     * 尺寸档位（留空取契约默认值）
     */
    private String sizeLabel;

    /**
     * 重绘强度档位（留空取契约默认值）
     */
    private String strengthLabel;

    /**
     * 出图工作流编码（留空取默认已发布契约）
     */
    private String workflowCode;

    /**
     * 生成张数（R0 只支持 1；字段先留着，避免前端以后改协议）
     */
    private Integer count = 1;

}
