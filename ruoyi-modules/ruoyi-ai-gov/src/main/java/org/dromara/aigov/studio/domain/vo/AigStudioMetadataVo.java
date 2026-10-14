package org.dromara.aigov.studio.domain.vo;

import lombok.Data;
import org.dromara.aigov.agent.enums.AigAgentCategoryEnum;
import org.dromara.aigov.studio.domain.AigStudioDraftContent;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 训练台元数据（设计 §9.2 {@code GET /aigov/studio/metadata}）。
 *
 * <p><b>为什么必须由服务端提供、不能让前端各自枚举</b>：类别、分节键这类"词表"一旦有两处
 * 定义就会漂移——前端多写一个类别，用户能选中却提交失败；少写一个，已支持的类别消失。
 * 设计 §2.3 记录的"界面说明仍写四类"正是这种漂移。让服务端成为唯一来源，前端只渲染。</p>
 *
 * @author ai-gov
 */
@Data
public class AigStudioMetadataVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分节键 → 中文标签（与设计 §6.3 的"业务表单字段"一一对应）。
     *
     * <p>用 {@code LinkedHashMap} 保持设计里的展示顺序。</p>
     */
    private static final Map<String, String> SECTION_LABELS = buildSectionLabels();

    /**
     * 可选类别
     */
    private List<CategoryItem> categories = new ArrayList<>();

    /**
     * Prompt 分节（键 + 标签）
     */
    private List<SectionItem> promptSections = new ArrayList<>();

    /**
     * 类别项。
     */
    @Data
    public static class CategoryItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 编码（入库值）
         */
        private String code;

        /**
         * 中文名
         */
        private String label;

        /**
         * 仓库里对应的现有实现（说明"包一层"而不是"重写"）
         */
        private String implementation;

        /**
         * 是否已有真实执行实现（{@code false} = 说明性占位，不能宣称可执行）
         */
        private Boolean executable;
    }

    /**
     * 分节项。
     */
    @Data
    public static class SectionItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 分节键（也是 Diff/预检的唯一口径）
         */
        private String key;

        /**
         * 中文标签
         */
        private String label;
    }

    /**
     * 组装元数据（服务端唯一词表来源）。
     *
     * @return 元数据
     */
    public static AigStudioMetadataVo build() {
        AigStudioMetadataVo vo = new AigStudioMetadataVo();
        for (AigAgentCategoryEnum item : AigAgentCategoryEnum.values()) {
            CategoryItem category = new CategoryItem();
            category.setCode(item.getCode());
            category.setLabel(item.getDesc());
            category.setImplementation(item.getImplementation());
            // ANALYSIS 的实现字段是说明性占位："暂无现有实现"——如实标为非可执行
            category.setExecutable(!item.getImplementation().contains("暂无现有实现")
                && !item.getImplementation().contains("占位"));
            vo.getCategories().add(category);
        }
        for (Map.Entry<String, String> entry : SECTION_LABELS.entrySet()) {
            SectionItem section = new SectionItem();
            section.setKey(entry.getKey());
            section.setLabel(entry.getValue());
            vo.getPromptSections().add(section);
        }
        return vo;
    }

    private static Map<String, String> buildSectionLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("role", "角色定位");
        labels.put("objective", "任务目标");
        labels.put("inputs", "输入");
        labels.put("workflow_rules", "流程规则");
        labels.put("tools_and_skills", "工具与技能");
        labels.put("constraints", "限制");
        labels.put("output_contract", "输出要求");
        labels.put("uncertainty_policy", "不确定处理");
        // 键集合必须与内容模型的唯一口径一致；不一致说明有人改了分节但没改标签
        if (!labels.keySet().equals(new java.util.LinkedHashSet<>(AigStudioDraftContent.PROMPT_SECTION_KEYS))) {
            throw new IllegalStateException("分节标签键与 AigStudioDraftContent.PROMPT_SECTION_KEYS 不一致："
                + labels.keySet() + " vs " + AigStudioDraftContent.PROMPT_SECTION_KEYS);
        }
        // 用 unmodifiableMap 保留插入顺序：Map.copyOf 不保证顺序，分节展示会乱
        return java.util.Collections.unmodifiableMap(labels);
    }

}
