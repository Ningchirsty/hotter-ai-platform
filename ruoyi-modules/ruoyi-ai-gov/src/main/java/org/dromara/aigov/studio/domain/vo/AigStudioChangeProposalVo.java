package org.dromara.aigov.studio.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.studio.domain.AigStudioChangeProposal;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 训练草稿变更提案视图（增量 21／附件 07 增量 6）。
 *
 * <p>{@code preview} 由 {@code previewJson} 解析而来：页面上要展示"这条提案会改哪几处"，
 * 让前端自己解析 JSON 就会多一份口径（前端解析失败时页面只会显示空白）。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigStudioChangeProposal.class)
public class AigStudioChangeProposalVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 提案ID
     */
    private Long proposalId;

    /**
     * 所属草稿
     */
    private Long draftId;

    /**
     * 提案基于的修订号
     */
    private Integer baseRevision;

    /**
     * 提案来源（{@code AigStudioProposalSourceEnum}）
     */
    private String source;

    /**
     * 改动预览（path/before/after）；由 previewJson 解析
     */
    private List<DiffItem> preview;

    /**
     * 提案时的内容哈希
     */
    private String contentHashBefore;

    /**
     * 应用后预计的内容哈希
     */
    private String contentHashAfter;

    /**
     * 状态（{@code AigStudioProposalStatusEnum}）
     */
    private String status;

    /**
     * 处理人
     */
    private Long decidedBy;

    /**
     * 处理时间
     */
    private LocalDateTime decidedAt;

    /**
     * 处理说明（拒绝原因；采纳时可为空）
     */
    private String decidedReason;

    /**
     * 提案说明
     */
    private String summary;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 一处改动。
     *
     * @param path   JSON Pointer 路径
     * @param before 改前值（展示用文本；空为 null）
     * @param after  改后值（展示用文本；空为 null）
     */
    public record DiffItem(String path, String before, String after) {
    }

}
