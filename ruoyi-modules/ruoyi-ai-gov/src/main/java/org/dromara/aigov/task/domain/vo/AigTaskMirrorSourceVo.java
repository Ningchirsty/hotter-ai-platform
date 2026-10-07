package org.dromara.aigov.task.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 可用的镜像来源（供前端渲染来源选择器）。
 *
 * <p>必须能枚举出来：列表接口要求指定来源，如果来源清单只能靠读代码知道，
 * 那个「必填」的硬约束就变成了使用障碍。</p>
 *
 * @author ai-gov
 */
@Data
@AllArgsConstructor
public class AigTaskMirrorSourceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 来源编码
     */
    private String source;

    /**
     * 来源展示名
     */
    private String label;

    /**
     * 说明（该来源的状态机与只读口径）
     */
    private String description;

}
