package org.dromara.aigov.studio.domain.bo;

import lombok.Data;

/**
 * 草案清单查询条件（三类草案共用）。
 *
 * <p><b>为什么这个可以共用、而实体/入参不共用</b>：查询条件只有"编码/名称/启用"三项，
 * 三类草案的这三项字面完全一样——分成三份只会让日后加一个筛选项要改三处。
 * 而实体与新增入参的字段确实不同（技能编码 vs 父 Agent vs 启动方式），那才是各自独立的部分。</p>
 *
 * @author ai-gov
 */
@Data
public class AigStudioArtifactDraftQueryBo {

    /**
     * 草案编码（模糊匹配）
     */
    private String draftCode;

    /**
     * 草案名称（模糊匹配）
     */
    private String draftName;

    /**
     * 是否启用（Y/N）
     */
    private String enabled;

    /**
     * 归属组织
     */
    private Long orgId;

}
