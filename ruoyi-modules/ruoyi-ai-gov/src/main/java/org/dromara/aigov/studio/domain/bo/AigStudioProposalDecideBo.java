package org.dromara.aigov.studio.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 处理变更提案的入参（采纳/拒绝共用；增量 21／附件 07 增量 6）。
 *
 * @author ai-gov
 */
@Data
public class AigStudioProposalDecideBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 期望的草稿修订号（必填）：采纳是写草稿，必须带 CAS——
     * 否则"你看到的提案 + 你以为的基础内容"与库里当前可能已经不是一回事。
     */
    private Integer expectedRevision;

    /**
     * 拒绝原因（可空；拒绝时给出比"已拒绝"更有用的信息）
     */
    private String reason;

}
