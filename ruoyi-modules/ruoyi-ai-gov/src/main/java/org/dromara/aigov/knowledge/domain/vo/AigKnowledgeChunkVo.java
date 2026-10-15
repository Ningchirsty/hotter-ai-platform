package org.dromara.aigov.knowledge.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识分段预览（设计 §6.4：「单份文档详情提供原文件信息、**解析预览、分段**、检索测试」）。
 *
 * <p><b>为什么只给"预览"而不是正文</b>：正文按设计 §10.2 走受控存储引用
 * （{@code aig_knowledge_chunk.text_ref}），本表只存受限长度的 {@code text_excerpt}。
 * 这个接口暴露的正是那段 excerpt —— 维护人据此判断"这一版解析/分段是不是靠谱"
 * （标题、页码、片段是否成句），而不会因为"想看看解析效果"就把整本资料拉进界面。</p>
 *
 * <p>{@code hasFullText} / {@code indexed} 如实告诉调用方"正文在哪、有没有进索引"：
 * 缺少这两个事实时，界面只能把"没有 excerpt"读成"没有正文"，而两者完全不是一回事。</p>
 *
 * @author ai-gov
 */
@Data
public class AigKnowledgeChunkVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分段ID */
    private Long chunkId;

    /** 段序（同版本内唯一） */
    private Integer ordinal;

    /** 来源定位（页码/标题/行列） */
    private String locator;

    /** 引用预览（受限长度） */
    private String textExcerpt;

    /** 片段数据等级 */
    private String dataLevel;

    /** 正文是否在受控存储里（true 表示"有正文但本接口不给"） */
    private Boolean hasFullText;

    /** 是否已进检索索引 */
    private Boolean indexed;

}
