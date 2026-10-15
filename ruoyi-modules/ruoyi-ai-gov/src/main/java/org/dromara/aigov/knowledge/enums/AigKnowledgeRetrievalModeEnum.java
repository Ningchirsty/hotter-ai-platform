package org.dromara.aigov.knowledge.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 检索测试模式（附件「关键词检索与向量检索可组合……排序参数通过评测确定」）。
 *
 * <h3>为什么先只给"两种模式"，不给"混合模式"</h3>
 * <p>"可组合"要回答的是<b>怎么排序</b>：关键词命中排前面、向量命中排前面、还是按加权分？
 * 这正好属于附件交给试点评测决定的"排序参数"。先把两种模式各自跑通、让人能对比它们在同一批
 * 资料上的表现，再按评测结论定权重——比现在拍一个权重、之后没人说得清它从哪来要可靠。
 * 所以本轮提供 {@link #KEYWORD} 与 {@link #VECTOR}，混合模式是评测结论的<b>产物</b>，不是前提。</p>
 *
 * <p><b>必须按"一次只验一路"来读结果</b>：{@link #VECTOR} 模式下命中的片段只来自向量索引，
 * 没有命中不代表资料里没有——它可能是"还没执行解析索引"，此时原因文案会说明（见检索实现）。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigKnowledgeRetrievalModeEnum {

    /**
     * 关键字检索（现状；正式执行路径也走这条）
     */
    KEYWORD("KEYWORD", "关键字"),

    /**
     * 向量检索（需要该版本已执行解析/索引；仅检索测试入口使用）
     */
    VECTOR("VECTOR", "向量");

    /**
     * 编码
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找（大小写不敏感）。
     *
     * @param code 编码（可空）
     * @return 枚举；空返回 {@link #KEYWORD}（保持既有行为），不认识返回 null
     */
    public static AigKnowledgeRetrievalModeEnum from(String code) {
        if (code == null || code.isBlank()) {
            return KEYWORD;
        }
        for (AigKnowledgeRetrievalModeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
