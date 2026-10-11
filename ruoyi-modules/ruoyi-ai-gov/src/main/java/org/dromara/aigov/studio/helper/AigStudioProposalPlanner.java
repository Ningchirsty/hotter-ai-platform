package org.dromara.aigov.studio.helper;

import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 变更提案的<b>白名单操作 + JSON Pointer 应用器</b>（增量 21／附件 07 增量 6，ST-003/ST-004）。
 *
 * <h3>这份白名单就是"Copilot 能改什么"的边界</h3>
 * <p>提案里的 {@code path} 必须命中白名单；命不中一律拒绝，而不是"尽力而为地应用一部分"。
 * 三个刻意<b>不</b>开放给提案的字段，各有理由：</p>
 * <ul>
 *     <li>{@code /agentCategory}：换类别等于换一个实现绑定，是独立变更（要动枚举与契约）；</li>
 *     <li>{@code /providerCapability}：它决定任务路由到哪条能力，属于<b>执行面</b>而不是文案面；</li>
 *     <li>{@code /allowExternal}：这是外发许可（安全口径），不能由一次"采纳建议"改掉。</li>
 * </ul>
 * <p>同理 {@code /inputSchema}、{@code /outputSchema}、{@code /pageCustomizationJson}
 * 也不开放：它们是结构性/可执行相关的字段，改动应当走人工直接编辑（有人负责、看得见）。</p>
 *
 * <h3>只做"设置"</h3>
 * <p>草稿内容是固定结构的对象，没有"删掉某节"这种有意义的改动——缺节会在提交预检时被报出来。
 * 因此操作只有"把某路径设成某值"，并且<b>同一路径在一份提案里只能出现一次</b>
 * （重复出现时"哪一次算数"取决于顺序，那是提案方不该拥有的权力）。</p>
 *
 * @author ai-gov
 */
public final class AigStudioProposalPlanner {

    /**
     * 文本字段：路径 → 读写一对。
     */
    private static final List<TextField> TEXT_FIELDS = List.of(
        new TextField("/agentName", AigStudioDraftContent::getAgentName, AigStudioDraftContent::setAgentName),
        new TextField("/roleDescription", AigStudioDraftContent::getRoleDescription,
            AigStudioDraftContent::setRoleDescription),
        new TextField("/objective", AigStudioDraftContent::getObjective, AigStudioDraftContent::setObjective),
        new TextField("/prohibitions", AigStudioDraftContent::getProhibitions,
            AigStudioDraftContent::setProhibitions),
        new TextField("/outputSummary", AigStudioDraftContent::getOutputSummary,
            AigStudioDraftContent::setOutputSummary),
        new TextField("/businessConstraints", AigStudioDraftContent::getBusinessConstraints,
            AigStudioDraftContent::setBusinessConstraints),
        new TextField("/modelRequirements", AigStudioDraftContent::getModelRequirements,
            AigStudioDraftContent::setModelRequirements),
        new TextField("/evaluationRules", AigStudioDraftContent::getEvaluationRules,
            AigStudioDraftContent::setEvaluationRules));

    /**
     * 清单字段：路径 → 读写一对（值必须是字符串数组）。
     */
    private static final List<ListField> LIST_FIELDS = List.of(
        new ListField("/coreCapabilities", AigStudioDraftContent::getCoreCapabilities,
            AigStudioDraftContent::setCoreCapabilities),
        new ListField("/allowedSkills", AigStudioDraftContent::getAllowedSkills,
            AigStudioDraftContent::setAllowedSkills),
        new ListField("/allowedTools", AigStudioDraftContent::getAllowedTools,
            AigStudioDraftContent::setAllowedTools),
        new ListField("/forbiddenTools", AigStudioDraftContent::getForbiddenTools,
            AigStudioDraftContent::setForbiddenTools),
        new ListField("/knowledgeScope", AigStudioDraftContent::getKnowledgeScope,
            AigStudioDraftContent::setKnowledgeScope));

    /**
     * Prompt 分节前缀（{@code /promptSections/<键>}）
     */
    private static final String PROMPT_PREFIX = "/promptSections/";

    /**
     * <b>刻意不开放</b>的结构性/安全字段（白名单的补集）。
     *
     * <p>它被两处用到：拒绝文案（列出来，调用方才知道自己踩了哪条），以及对话式 Copilot 的提示词
     * （明确告诉模型这些不能碰）。把"不能改什么"只写在提示词里、代码里靠 else 分支兜底是可以的；
     * 反过来只写在代码里、提示词不提，模型就会持续输出一个必然被拒绝的操作。</p>
     */
    public static final List<String> RESERVED_PATHS = List.of(
        "/agentCategory", "/providerCapability", "/allowExternal",
        "/inputSchema", "/outputSchema", "/pageCustomizationJson");

    private AigStudioProposalPlanner() {
    }

    /**
     * 白名单路径全集（文本字段 → 清单字段 → 展开的八个 Prompt 分节）。
     *
     * <p><b>提示词必须由它生成，而不是手工抄一份</b>：抄一份的结果是"代码改了、提示词没改"，
     * 于是模型会一直输出一个必然被拒绝的操作——而那条错误看起来像模型不行，实际是提示词过期。</p>
     *
     * @return 路径清单（顺序固定：文本、清单、分节）
     */
    public static List<String> allowedPaths() {
        List<String> paths = new ArrayList<>();
        for (TextField field : TEXT_FIELDS) {
            paths.add(field.path());
        }
        for (ListField field : LIST_FIELDS) {
            paths.add(field.path());
        }
        for (String key : AigStudioDraftContent.PROMPT_SECTION_KEYS) {
            paths.add(PROMPT_PREFIX + key);
        }
        return List.copyOf(paths);
    }

    /**
     * 一条操作（路径 + 新值）。
     *
     * @param path  JSON Pointer 路径
     * @param value 新值（文本路径要求 String；清单路径要求 List&lt;String&gt;）
     */
    public record Operation(String path, Object value) {
    }

    /**
     * 一处改动（供人审阅的预览）。
     *
     * @param path   路径
     * @param before 改前值（展示文本；为空时 null）
     * @param after  改后值（展示文本；为空时 null）
     */
    public record DiffItem(String path, String before, String after) {
    }

    /**
     * 把一组操作应用到草稿内容上。
     *
     * <p><b>它会修改传入的 {@code content}</b>：调用方必须传一份"本次请求刚解析出来的"对象
     * （服务层就是这么做的），不要传共享实例。</p>
     *
     * @param content    草稿内容（会就地修改）
     * @param operations 操作清单
     * @return 改动预览（按操作顺序）
     * @throws IllegalArgumentException 操作非法（空清单、路径不在白名单、值类型不符、同路径重复）
     */
    public static List<DiffItem> apply(AigStudioDraftContent content, List<Operation> operations) {
        if (content == null) {
            throw new IllegalArgumentException("草稿内容为空，无法应用提案");
        }
        if (operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException("提案至少要有一处改动");
        }
        Set<String> seen = new LinkedHashSet<>();
        List<DiffItem> diff = new ArrayList<>();
        for (Operation operation : operations) {
            if (operation == null || StringUtils.isBlank(operation.path())) {
                throw new IllegalArgumentException("提案里有一处改动缺少路径（path）");
            }
            String path = operation.path().trim();
            if (!seen.add(path)) {
                throw new IllegalArgumentException("同一路径在提案里出现了多次：" + path
                    + "（哪一次算数会取决于顺序，平台不接受这种歧义）");
            }
            diff.add(applyOne(content, path, operation.value()));
        }
        return diff;
    }

    /**
     * 应用一处改动。
     *
     * @param content 内容（就地修改）
     * @param path    路径
     * @param value   新值
     * @return 改动预览
     * @throws IllegalArgumentException 路径不在白名单或值类型不符
     */
    private static DiffItem applyOne(AigStudioDraftContent content, String path, Object value) {
        for (TextField field : TEXT_FIELDS) {
            if (field.path().equals(path)) {
                String text = requireText(path, value);
                String before = field.getter().apply(content);
                field.setter().accept(content, text);
                return new DiffItem(path, before, text);
            }
        }
        for (ListField field : LIST_FIELDS) {
            if (field.path().equals(path)) {
                List<String> list = requireList(path, value);
                List<String> before = field.getter().apply(content);
                field.setter().accept(content, list);
                return new DiffItem(path, render(before), render(list));
            }
        }
        if (path.startsWith(PROMPT_PREFIX)) {
            String key = path.substring(PROMPT_PREFIX.length());
            if (!AigStudioDraftContent.PROMPT_SECTION_KEYS.contains(key)) {
                throw new IllegalArgumentException("不认识的 Prompt 分节：" + key
                    + "（可选：" + AigStudioDraftContent.PROMPT_SECTION_KEYS + "）");
            }
            String text = requireText(path, value);
            String before = content.getPromptSections().get(key);
            content.getPromptSections().put(key, text);
            return new DiffItem(path, before, text);
        }
        throw new IllegalArgumentException("提案不允许改这个字段：" + path
            + "（只允许改文案/清单/主 Prompt 分节；不开放的是：" + String.join("、", RESERVED_PATHS) + "）");
    }

    /**
     * 文本值校验。
     *
     * @param path  路径
     * @param value 值
     * @return 文本
     * @throws IllegalArgumentException 不是文本
     */
    private static String requireText(String path, Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("路径 " + path + " 需要文本值，实际类型="
                + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        return text;
    }

    /**
     * 清单值校验（字符串数组）。
     *
     * @param path  路径
     * @param value 值
     * @return 清单
     * @throws IllegalArgumentException 不是字符串数组
     */
    private static List<String> requireList(String path, Object value) {
        if (!(value instanceof List<?> raw)) {
            throw new IllegalArgumentException("路径 " + path + " 需要字符串数组，实际类型="
                + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        List<String> items = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (!(item instanceof String text)) {
                throw new IllegalArgumentException("路径 " + path + " 的数组元素必须都是字符串，实际=" + item);
            }
            items.add(text);
        }
        return items;
    }

    /**
     * 清单的展示文本。
     *
     * @param items 清单
     * @return 展示文本；空为 null
     */
    private static String render(List<String> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return String.join("，", items);
    }

    /**
     * 文本字段的读写对。
     *
     * @param path   路径
     * @param getter 读
     * @param setter 写
     */
    private record TextField(String path, Function<AigStudioDraftContent, String> getter,
                             BiConsumer<AigStudioDraftContent, String> setter) {
    }

    /**
     * 清单字段的读写对。
     *
     * @param path   路径
     * @param getter 读
     * @param setter 写
     */
    private record ListField(String path, Function<AigStudioDraftContent, List<String>> getter,
                             BiConsumer<AigStudioDraftContent, List<String>> setter) {
    }

}
