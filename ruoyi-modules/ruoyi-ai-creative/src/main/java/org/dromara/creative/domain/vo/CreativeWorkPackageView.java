package org.dromara.creative.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 品牌部签发的**开工包**在视觉工厂侧的只读视图（内测 C5①）。
 *
 * <p><b>它为什么存在</b>：开工包原先在内容侧签发，而设计侧<b>零引用</b>——
 * 连字段都没有，"交接"只是内容侧的单方面动作（内测 S5）。
 * 现在开工包的定位是**跨部门交接凭证**，所以设计侧必须能读到它。</p>
 *
 * <p><b>为什么内容原样透传 {@code contentJson} 而不是拆成结构化字段</b>：
 * 包的结构属于内容域（SPEC §4.5），创作域只是读者。拆成字段就等于在创作域
 * 维护第二份结构定义，内容侧一改结构这边就悄悄错位——而"悄悄错位"正是这一轮
 * 反复在修的东西。前端按同一份结构解析，解析不了就原样展示并说明。</p>
 *
 * <p><b>{@code renderOutputSize} 由创作域补</b>：尺寸的权威是创作域的场景配置
 * （见 {@link org.dromara.creative.helper.CreativeOutputSpecs}），内容侧的
 * {@code spec.outputSize} 是"品牌部确认的尺寸要求"——两个含义不同的东西同时摆在交接页上，
 * 由人裁定，而不是让一边去覆盖另一边。</p>
 *
 * @author creative
 */
@Data
public class CreativeWorkPackageView implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否已有开工包（false＝品牌部还没生成；此时其余字段无意义）
     */
    private boolean available;

    private Long packageId;

    /**
     * 冻结的事实版本（签发时定下）
     */
    private Integer snapshotVersion;

    /**
     * DRAFT（草稿，未签发）/ ISSUED（已签发）
     */
    private String status;

    private String statusDesc;

    private Long generatedBy;

    private LocalDateTime generatedAt;

    private Long issuedBy;

    /**
     * 签发人昵称（内容域解析；查不到时为 null，页面回落到显示ID）
     */
    private String issuedByName;

    private LocalDateTime issuedAt;

    /**
     * 包内容原文（结构见 SPEC §4.5，由内容域生成；本域只读透传）
     */
    private String contentJson;

    /**
     * 本交付类型排版实际使用的输出规格（创作域补，来自 dp_output_spec 默认项）
     */
    private String renderOutputSize;

    /**
     * 这份包在跨部门协作里的地位（页面直接显示，避免各处自行解释）
     */
    private String note;
}
