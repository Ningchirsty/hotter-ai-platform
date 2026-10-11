package org.dromara.aigov.studio.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 把草案生成物落到某个训练草稿的入参（附件 07 增量 7）。
 *
 * <p><b>为什么必须带 {@code expectedRevision}</b>：生成是<b>写草稿</b>（产生一条新修订），
 * 写草稿必须带 CAS。否则"你看到的草案 + 你以为的目标草稿内容"与库里当前可能已经不是一回事，
 * 生成物会被盖到别人刚改过的内容上。</p>
 *
 * @author ai-gov
 */
@Data
public class AigStudioArtifactGenerateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标训练草稿ID（必填）
     */
    @NotNull(message = "目标训练草稿ID不能为空")
    private Long targetDraftId;

    /**
     * 期望的目标草稿当前修订号（必填；CAS）
     */
    @NotNull(message = "期望修订号（expectedRevision）不能为空：写草稿必须带版本")
    private Integer expectedRevision;

}
