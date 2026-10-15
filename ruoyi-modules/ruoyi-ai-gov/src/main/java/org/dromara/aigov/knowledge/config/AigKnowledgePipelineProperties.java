package org.dromara.aigov.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识处理管道参数（{@code aigov.knowledge.pipeline.*}，设计 §6.4、§7.4）。
 *
 * <h3>为什么这些值必须可配、且默认值要写清理由</h3>
 * <p>附件对知识处理引擎的要求是「优先内部可部署，按试点评测选型」。也就是说：<b>引擎可以先给一个
 * 内部可部署的基线</b>，但<b>分段长度、嵌入维度、排序参数这类会直接影响检索效果的值，最终由试点评测
 * 定</b>。把它们的默认值写在这里、并且可以被配置覆盖，就避免了"效果不好时只能改代码、改完没人知道
 * 之前跑的是什么"。</p>
 *
 * <h3>两个"不承诺"</h3>
 * <ul>
 *     <li><b>不承诺准确率</b>：默认的 {@code builtin-hash-ngram-v1} 嵌入是<b>可用性基线</b>，不是效果
 *         结论；</li>
 *     <li><b>不承诺向量属于本库</b>：向量写到哪里由 {@code KnowledgeVectorIndexPort} 决定，默认实现
 *         落本地磁盘，属于<b>单节点试点可用</b>的形态，多副本部署前必须先选型（见
 *         {@link #vectorIndexDir} 的注释）。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.knowledge.pipeline")
public class AigKnowledgePipelineProperties {

    /**
     * 指定使用的解析引擎 ID（空 = 按来源类型用内置默认引擎）。
     *
     * <p>这是"按试点评测选型"的落地开关：评测结论出来之前，代码里不写死用哪个引擎；
     * 结论出来之后，把胜出的引擎实现挂进来、在这里填它的 {@code engineId} 即可切换，
     * 不需要改业务代码。填了一个不认识/不支持的 ID 时会<b>显式报错</b>，而不是悄悄退回默认——
     * 否则"我明明切了引擎，结果还是老效果"会非常难查。</p>
     */
    private String parserEngineId = "";

    /**
     * 分段目标长度（字符，默认 800）。
     *
     * <p>取 800 的理由：{@code aig_knowledge_chunk.text_excerpt} 是 {@code varchar(1000)}，
     * 目标长度小于它，才能让"引用预览"覆盖绝大多数分段的<b>完整正文</b>——否则关键词检索只能命中
     * 每条分段的前 1000 字，越靠后的内容越像"不存在"。</p>
     */
    private int chunkTargetChars = 800;

    /**
     * 同一来源块内部超长文本的重叠长度（字符，默认 100）。
     *
     * <p><b>只在同一来源块内部重叠</b>：跨来源块的"重叠"会把两个页码的正文并进同一条分段，
     * 而一条分段只能有一个定位，引用就必然指向其中一处——那等于让引用悄悄说谎。</p>
     */
    private int chunkOverlapChars = 100;

    /**
     * 一个版本最多允许的分段数（默认 2000）。
     *
     * <p>超限<b>直接失败</b>，不截断：只索引前 2000 段会让检索结果"看起来正常、其实资料不全"，
     * 这种错误没有任何界面信号。宁可让处理失败并给出可读原因。</p>
     */
    private int maxChunksPerVersion = 2000;

    /**
     * 嵌入维度（默认 256）。
     *
     * <p>内置于本进程的哈希嵌入用这个维度<b>定长</b>输出；换嵌入模型时这个值会跟着模型变，
     * 所以它属于"评测后确定"的参数。</p>
     */
    private int embeddingDimension = 256;

    /**
     * 向量索引目录（空 = {@code ${java.io.tmpdir}/aigov-knowledge-vector-index}）。
     *
     * <p><b>默认落本地磁盘是刻意为之，也是一个已知边界</b>：它让"内部可部署"在没有引入任何
     * 外部服务的前提下成立（试点单节点可跑通），但它<b>不是多副本方案</b>——多副本时每个实例
     * 只看得见自己写的文件。正式部署前必须按评测结论换成共享向量存储实现，本属性就是那时的接入点。</p>
     */
    private String vectorIndexDir = "";

    /**
     * 引用预览的最大字符数（默认 1000，必须与 {@code text_excerpt} 列宽一致）。
     */
    private int excerptMaxChars = 1000;

    /**
     * 是否启用向量检索（检索测试的 {@code VECTOR}/{@code HYBRID} 模式）。
     *
     * <p>默认<b>开</b>：只影响显式的检索测试入口，正式执行路径仍走关键字，不会因为打开它而
     * 改变线上回答的排序。</p>
     */
    private boolean vectorSearchEnabled = true;

    /**
     * 生效的向量索引目录。
     *
     * @return 配置值；未配置时取临时目录下的固定子目录
     */
    public String effectiveVectorIndexDir() {
        if (vectorIndexDir == null || vectorIndexDir.isBlank()) {
            return System.getProperty("java.io.tmpdir") + java.io.File.separator
                + "aigov-knowledge-vector-index";
        }
        return vectorIndexDir.trim();
    }

}
