package org.dromara.aigov.studio.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * Agent Studio 草稿修订 {@code aig_studio_revision}（专题 C §C2.1、§C8）。
 *
 * <p><b>修订是不可变快照</b>：一旦写入就不再修改。要"回到旧版本"不是改这条记录，
 * 而是产生一条内容相同、来源为 {@code ROLLBACK} 的新修订——这样"曾经改过什么"
 * 永远查得到，也不会出现"历史修订被悄悄改写"。这与本仓 {@code aig_release_event}
 * 的追加式账本同一思路。</p>
 *
 * <p><b>内容快照与哈希一起存</b>：只存哈希会让人无法复核"它当时到底长什么样"；
 * 只存内容则每次比对都要重算。两者同存，校验与复核都便宜。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_studio_revision")
public class AigStudioRevision extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 修订ID
     */
    @TableId(value = "revision_id")
    private Long revisionId;

    /**
     * 所属草稿
     */
    private Long draftId;

    /**
     * 修订号（草稿内唯一，从 1 开始）
     */
    private Integer revisionNo;

    /**
     * 该修订的完整内容快照（不可变）
     */
    private String contentSnapshotJson;

    /**
     * 内容哈希（sha256 规范化 JSON）
     */
    private String contentHash;

    /**
     * 修订人
     */
    private Long authorId;

    /**
     * 修订来源（{@code AigStudioRevisionSourceEnum}）
     */
    private String source;

    /**
     * 修订说明（页面上的"改了什么"）
     */
    private String summary;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}
