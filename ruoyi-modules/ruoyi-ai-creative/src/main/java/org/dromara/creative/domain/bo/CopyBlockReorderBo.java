package org.dromara.creative.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文案块重排表单（拖拽排序用）。
 *
 * <p>{@code ids} 的顺序就是目标顺序：服务端按下标重写 sort_no。
 * 数组里出现不属于该「项目 + 块类型」的 id 时直接报错，不静默跳过——
 * 静默跳过会让人以为「拖了但没生效是页面卡了」，而真实原因是数据不属于同一组。</p>
 *
 * @author creative
 */
@Data
public class CopyBlockReorderBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 块类型（必填：排序只在同一类型内进行）
     */
    private String blockType;

    /**
     * 目标顺序的块ID列表
     */
    @NotEmpty(message = "重排的块ID列表不能为空")
    private List<Long> ids;

}
