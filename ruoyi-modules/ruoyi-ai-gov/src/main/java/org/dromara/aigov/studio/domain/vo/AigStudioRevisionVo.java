package org.dromara.aigov.studio.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.studio.domain.AigStudioRevision;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 草稿修订视图（版本记录/历史列表用）。
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigStudioRevision.class)
public class AigStudioRevisionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 修订ID
     */
    private Long revisionId;

    /**
     * 所属草稿
     */
    private Long draftId;

    /**
     * 修订号
     */
    private Integer revisionNo;

    /**
     * 内容快照（列表接口可不下发；详情/Diff 时用）
     */
    private String contentSnapshotJson;

    /**
     * 内容哈希
     */
    private String contentHash;

    /**
     * 修订人
     */
    private Long authorId;

    /**
     * 修订来源（{@code AigStudioRevisionSourceEnum} 的 code）
     */
    private String source;

    /**
     * 修订说明
     */
    private String summary;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
