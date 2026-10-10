package org.dromara.aigov.studio.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提交草稿的结果。
 *
 * <p><b>{@link #releaseStatus} 只会是 {@code DRAFT}</b>：训练台不做发布推进，
 * 后续的沙箱/黄金用例/人工批准/灰度仍由既有状态机（五道门槛）走。
 * 把它返回出来是为了让调用方一眼看到"这只是一条草稿版本"。</p>
 *
 * @author ai-gov
 */
@Data
public class AigStudioDraftSubmitVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 草稿ID
     */
    private Long draftId;

    /**
     * 提交时的修订号（证明固化的是哪一版内容）
     */
    private Integer revision;

    /**
     * 内容哈希（同上）
     */
    private String contentHash;

    /**
     * Agent 定义ID（原本已绑定的，或本次从零创建的）
     */
    private Long agentId;

    /**
     * Agent 编码
     */
    private String agentCode;

    /**
     * 本次是否新建了 Agent 定义（false = 复用了草稿已绑定的那个）
     */
    private Boolean agentCreated;

    /**
     * 产生的 Agent 版本ID
     */
    private Long agentVersionId;

    /**
     * 版本号
     */
    private String version;

    /**
     * 发布状态（固定 DRAFT）
     */
    private String releaseStatus;

}
