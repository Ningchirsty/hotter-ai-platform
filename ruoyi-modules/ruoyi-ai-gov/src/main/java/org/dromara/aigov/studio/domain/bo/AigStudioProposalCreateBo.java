package org.dromara.aigov.studio.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 创建变更提案的入参（增量 21／附件 07 增量 6）。
 *
 * @author ai-gov
 */
@Data
public class AigStudioProposalCreateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 提案基于的修订号（必填）。必须等于草稿当前修订——针对旧内容的提案一定已经失效。
     */
    private Integer baseRevision;

    /**
     * 改动清单（必填，非空）。每项是"白名单路径 + 新值"。
     */
    private List<AigStudioProposalOperationBo> operations;

    /**
     * 提案说明（可空；页面展示"想改什么"）
     */
    private String summary;

    /**
     * 来源（{@code AigStudioProposalSourceEnum}；可空=COPILOT）
     */
    private String source;

}
