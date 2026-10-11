package org.dromara.aigov.studio.domain.vo;

import lombok.Data;
import org.dromara.aigov.studio.domain.AigStudioDraftContent;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 草案生成结果（预览与落库共用同一个视图；附件 07 增量 7）。
 *
 * <p><b>为什么预览与生成共用一个视图</b>：页面要让人"先看清生成物、再决定落到哪个草稿"。
 * 如果预览和生成各返回一套结构，就会出现"预览看的是 A、生成的是 B"——而生成是<b>写草稿</b>，
 * 一次不一致就会把没被人看过的东西写进内容。</p>
 *
 * <p>{@code preview} 时 {@code targetDraftId/revision/contentHash/revisionCreated} 为空；
 * {@code generate} 时填上落库结果。</p>
 *
 * @author ai-gov
 */
@Data
public class AigStudioArtifactGenerateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 草案类型（{@code AigStudioArtifactKindEnum} 的 code）
     */
    private String kind;

    /**
     * 源草案ID
     */
    private Long sourceDraftId;

    /**
     * 源草案编码（页面上认人）
     */
    private String sourceDraftCode;

    /**
     * 生成的可提交内容（结构化；页面据此渲染八分节与声明字段）
     */
    private AigStudioDraftContent content;

    /**
     * 没写进内容、以及留空待补的说明（人读；不要静默丢字段）
     */
    private List<String> notes;

    /**
     * 目标训练草稿ID（仅 generate 时非空）
     */
    private Long targetDraftId;

    /**
     * 落库后的修订号（仅 generate 时非空）
     */
    private Integer revision;

    /**
     * 落库后的内容哈希（仅 generate 时非空）
     */
    private String contentHash;

    /**
     * 是否产生了新修订（仅 generate 时为 true；内容与目标草稿一致时会被拒绝，不会返回 false）
     */
    private Boolean revisionCreated;

}
