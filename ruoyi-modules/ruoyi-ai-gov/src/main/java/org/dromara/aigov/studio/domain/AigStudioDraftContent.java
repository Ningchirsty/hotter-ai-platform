package org.dromara.aigov.studio.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Studio 草稿的结构化内容（专题 C §C3 左栏 + §C5.1 Prompt 分节）。
 *
 * <p><b>为什么用一个强类型对象而不是"随便一段 JSON"</b>：草稿要能
 * ①按分节做 Diff、②把 Prompt 结构映射到 {@code aig_agent_version} 的既有列
 * （{@code prompt_template}/{@code input_schema}/{@code output_schema}/{@code provider_capability}/
 * {@code allow_external}），③被规范化后算稳定哈希。自由 JSON 三者都做不到。</p>
 *
 * <p><b>与 {@code aig_agent_version} 的关系</b>：本类的字段是"草稿态"，
 * 不等于正式版本；提交（submit）时才把其中的子集写进一条新的
 * {@code aig_agent_version}（{@code release_status=DRAFT}），并交由既有五道门槛状态机推进。</p>
 *
 * <p><b>工具/知识在 P0 只是"声明"</b>：本仓没有 Tool Gateway / Knowledge 绑定表，
 * 所以这里存的是声明引用（{@code allowedTools} 等），页面上如实标注"仅声明"，
 * 不假装已经生效。</p>
 *
 * <p><b>为什么 {@code @JsonInclude(NON_NULL)} 是必需的、不是顺手加的</b>：内容哈希有两条路径——
 * ①写入草稿时对**调用方提交的那串 JSON** 规范化后算（{@code AigStudioDraftServiceImpl#createDraft}）；
 * ②提案/生成等改动路径把 {@code contentJson} 解析成对象、再序列化后算（{@code AigStudioProposalServiceImpl#hashOf}）。
 * 两条路径必须给出**同一个哈希**，否则「内容没变」的判断会静默失效：
 * 生产实测（2026-10-12）把 {@code /objective} 改成与草稿**完全相同**的值，提案仍被接受、并落了库。
 * 根因就是调用方省略了可选字段（如 {@code pageCustomizationJson}），解析后再序列化时被补成 {@code null}
 * ⇒ 规范化结果多了一个键 ⇒ 哈希不等。加上 NON_NULL 后，"不传"与"传 null"在内容语义上等价，
 * 两条路径重新一致（{@code AigStudioContentHasherTest#submittedJsonAndRoundTrippedObjectAgreeOnHash} 钉住）。</p>
 *
 * @author ai-gov
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AigStudioDraftContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Prompt 分节的标准键（顺序即页面展示顺序，也是 §C5.1 定义的八个分节）。
     *
     * <p>固定键的意义：Diff 与"缺了哪一节"的校验都要有唯一口径；
     * 若允许随便起名，两个人都写了"输出要求"，Diff 就永远对不上。</p>
     */
    public static final List<String> PROMPT_SECTION_KEYS = List.of(
        "role", "objective", "inputs", "workflow_rules",
        "tools_and_skills", "constraints", "output_contract", "uncertainty_policy");

    /**
     * Agent 名称（展示用；正式名以 aig_agent.agent_name 为准）
     */
    private String agentName;

    /**
     * Agent 类别（{@code AigAgentCategoryEnum} 的 code）。
     *
     * <p><b>为什么必须由草稿显式声明、不自动猜</b>：本仓当前只有四个类别，且各自绑定了具体的
     * 创作工厂实现（PLANNING=详情页策划、VISUAL_DNA=视觉 DNA、GENERATION=生成任务构建、QA=视觉 QA）。
     * "从零创建"时若由服务层替它挑一个，会挑出一个与内容毫不相干的实现绑定；
     * 而新增类别是一次独立变更（动枚举与契约），不在本增量内。
     * 因此：草稿必须写明是四者中的哪一个，否则提交时明确拒绝并说清原因。</p>
     */
    private String agentCategory;

    /**
     * 角色定位（§C3 左栏"角色定位"）
     */
    private String roleDescription;

    /**
     * 主要责任（做什么）
     */
    private String objective;

    /**
     * 禁止事项（不做什么）
     */
    private String prohibitions;

    /**
     * 核心能力（人读清单，§C3"核心能力"）
     */
    private List<String> coreCapabilities;

    /**
     * 输出成果说明（业务语言）
     */
    private String outputSummary;

    /**
     * 业务约束（品牌/合规/事实来源等）
     */
    private String businessConstraints;

    /**
     * 主 Prompt 分节：键取自 {@link #PROMPT_SECTION_KEYS}，值是该节正文。
     *
     * <p>用 {@code LinkedHashMap} 只为让序列化结果对人有可读顺序；<b>哈希不依赖顺序</b>
     * （规范化时会按键排序）。</p>
     */
    private Map<String, String> promptSections = new LinkedHashMap<>();

    /**
     * 输入 Schema（JSON 文本；提交时落 aig_agent_version.input_schema）
     */
    private String inputSchema;

    /**
     * 输出 Schema（JSON 文本；提交时落 aig_agent_version.output_schema）
     */
    private String outputSchema;

    /**
     * 所需 Provider 能力编码（如 {@code image_generation}；路由只认能力不认厂商）
     */
    private String providerCapability;

    /**
     * 是否允许外部调用（Y/N；与路由策略取与，两者都允许才可能外发）
     */
    private String allowExternal;

    /**
     * 需要的模型能力要求（人读，如"结构化 JSON 输出、长上下文"）
     */
    private String modelRequirements;

    /**
     * 引用的 Skill 编码（P0 仅声明）
     */
    private List<String> allowedSkills;

    /**
     * 允许的工具（P0 仅声明；本仓无 Tool Gateway）
     */
    private List<String> allowedTools;

    /**
     * 禁止的工具（P0 仅声明）
     */
    private List<String> forbiddenTools;

    /**
     * 知识范围（P0 仅声明；本仓无知识库绑定表）
     */
    private List<String> knowledgeScope;

    /**
     * 评测规则说明（黄金用例的补充口径；正式用例仍在 aig_evaluation_case）
     */
    private String evaluationRules;

    /**
     * 页面定制（头像/横幅/卡片等；P0 只存不渲染，避免"任意 JavaScript 动态 UI"）
     */
    private String pageCustomizationJson;

}
