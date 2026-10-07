package org.dromara.aigov.task.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 只读镜像查询入参（设计 §9 统一任务视图的「存量只读」一侧）。
 *
 * <p><b>为什么 {@code source} 必填、不提供「跨来源合并分页」</b>：各来源的分页语义不同
 * （有的是流式游标、有的按 id 倒序、总数含义也未必一致）。把两套来源合成一页，
 * 页码、总数、排序都会骗人，而且没人能从返回体里看出来它是合成的。
 * 因此列表**必须指定来源**，来源清单由 {@code GET /aigov/task/mirror/sources} 给出。</p>
 *
 * <p><b>为什么不放 {@code keyword}</b>：镜像层只暴露<b>来源真的支持</b>的过滤条件。
 * 放一个来源不支持的字段，风险是它被静默忽略——使用者以为筛过了，看到的却是全量，
 * 这类「看起来筛了其实没筛」比不提供筛选更糟。需要更多筛选时，
 * 由来源侧明确实现后再加进来。</p>
 *
 * @author ai-gov
 */
@Data
public class AigTaskMirrorQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 镜像来源编码（由 {@code IAigTaskMirrorProvider#source()} 声明），必填
     */
    @NotBlank(message = "镜像来源不能为空")
    @Size(max = 32, message = "镜像来源长度不能超过 32")
    private String source;

    /**
     * 来源自己的状态编码（原样透传，<b>不做跨来源状态映射</b>）
     */
    @Size(max = 32, message = "状态长度不能超过 32")
    private String status;

    /**
     * 来源侧的业务对象ID（对创作域即 {@code dp_generation.task_id}）
     */
    private Long projectId;

}
