package org.dromara.hrtalent.domain.bo.talent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 人才合并业务对象（SPEC-P4 §2.5 POST /talent/duplicates/{id}/merge，设计文档 §8.18、§21.7）。
 *
 * <p><b>这是本模块风险最高的写操作</b>：一次调用会在<b>单个数据库事务</b>内把「被合并主档」的
 * 全部关系转移到「保留主档」，并把被合并主档标记为 {@code merged}。</p>
 *
 * <p><b>必填校验</b>：保留主档与被合并主档的 ID 与 {@code version} 都必须回传
 * （设计文档 §9.6 乐观锁、§11.1「合并接口必须校验版本」）。</p>
 *
 * <p><b>冲突字段取值</b>：{@link #fieldDecisions} 只对「两边都有值且不相同」的冲突字段生效；
 * 未出现的冲突字段一律保留「保留主档」的现值（保守策略：不因未选择而丢数据）。
 * 允许的字段名见 {@link TalentMergeFields}。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentMergeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 保留（主）人才主档ID
     */
    @NotNull(message = "保留主档ID不能为空")
    private Long keepTalentId;

    /**
     * 保留主档的乐观锁版本号（必须回传，用于并发校验）
     */
    @NotNull(message = "保留主档版本号不能为空，请刷新后重试")
    private Integer keepVersion;

    /**
     * 被合并（从）人才主档ID
     */
    @NotNull(message = "被合并主档ID不能为空")
    private Long mergedTalentId;

    /**
     * 被合并主档的乐观锁版本号（必须回传，用于并发校验）
     */
    @NotNull(message = "被合并主档版本号不能为空，请刷新后重试")
    private Integer mergedVersion;

    /**
     * 合并原因（必填，写入合并日志并留痕）
     */
    @NotBlank(message = "合并原因不能为空")
    private String mergeReason;

    /**
     * 冲突字段取值决策（为空表示所有冲突字段均保留「保留主档」的现值）
     */
    @Valid
    private List<FieldDecision> fieldDecisions;

    /**
     * 单个冲突字段的保留选择。
     *
     * <p>{@link #from} 只接受 {@code keep} 或 {@code merged}：前者表示采用保留主档的值，
     * 后者表示采用被合并主档的值。任何其他取值都会被服务层拒绝（fail-safe）。</p>
     *
     * @author hr-talent
     */
    @Data
    public static class FieldDecision implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 字段名（见 {@link TalentMergeFields}）
         */
        @NotBlank(message = "冲突字段名不能为空")
        private String field;

        /**
         * 取值来源（keep 保留主档 / merged 被合并主档）
         */
        @NotBlank(message = "冲突字段取值来源不能为空（keep 或 merged）")
        private String from;

    }

}
