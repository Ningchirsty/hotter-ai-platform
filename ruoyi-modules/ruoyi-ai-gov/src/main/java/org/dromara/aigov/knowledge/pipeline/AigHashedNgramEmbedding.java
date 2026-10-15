package org.dromara.aigov.knowledge.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.knowledge.config.AigKnowledgePipelineProperties;
import org.springframework.stereotype.Component;

/**
 * 内置哈希嵌入（{@value #ENGINE_ID}）：<b>内部可部署的可用性基线，不承诺效果</b>。
 *
 * <h3>它是干什么的、不是干什么的</h3>
 * <p>它的作用不是"达到可用准确率"，而是让<b>整条知识链路在没有选定模型前就能端到端跑通</b>：
 * 解析 → 分段 → 嵌入 → 写索引 → 向量检索测试。这样试点评测可以直接拿真实资料对比
 * "关键词 / 向量 / 混合"三种模式，而不是对着一个空实现讨论选型。</p>
 *
 * <p>附件要求"优先内部可部署、按试点评测选型"：本实现零依赖、零外发、纯内存、确定性，
 * 满足"内部可部署"；而它<b>是否够用，由评测结论回答</b>，代码与文档都不作承诺。</p>
 *
 * <h3>算法（可复核，而非"随便哈希一下"）</h3>
 * <ol>
 *     <li><b>分词</b>：中日韩字符按"单字 + 相邻双字"出词（中文没有空格，单字区分度低、双字命中率高）；
 *         拉丁字母/数字按单词切，统一转小写；</li>
 *     <li><b>哈希</b>：FNV-1a 64 位（不依赖 {@code String.hashCode()} 的跨版本稳定性）；</li>
 *     <li><b>投放</b>：桶 = {@code h % dim}，符号取哈希的另一位——符号位是为了让不同词的桶内碰撞
 *         <b>相互抵消而不是相互累加</b>（否则高频词的桶会无限涨，余弦相似度被高频词主导）；</li>
 *     <li><b>归一化</b>：L2 归一化，使余弦相似度只反映方向、不受文本长度影响。</li>
 * </ol>
 *
 * <h3>它的已知弱点（评测时应当能观察出来，而不是被藏起来）</h3>
 * <ul>
 *     <li>同义不同词（"手机壳" vs "保护套"）<b>完全不相似</b>——哈希嵌入没有语义；</li>
 *     <li>它的相似度大致等价于"字面重叠"，所以它对"关键词检索漏掉的那些表达"几乎没有帮助；</li>
 *     <li>因此试点评测的判据里必须包含"同义改写查询"这一类，否则会得出"向量没用"的错误结论。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Slf4j
@Component
public class AigHashedNgramEmbedding implements KnowledgeEmbeddingPort {

    /**
     * 引擎 ID（{@code v1} 表示算法版本；换算法要改版本号，不能悄悄改变已有向量的含义）。
     */
    public static final String ENGINE_ID = "builtin-hash-ngram-v1";

    /**
     * 最小维度：维度太小（如 8）会让桶碰撞变成主导噪声，此时的"相似度"基本是随机数。
     */
    static final int MIN_DIMENSION = 32;

    private final int dimension;

    /**
     * 构造。
     *
     * @param properties 管道参数（取 {@code embedding-dimension}）
     */
    public AigHashedNgramEmbedding(AigKnowledgePipelineProperties properties) {
        int configured = properties.getEmbeddingDimension();
        if (configured < MIN_DIMENSION) {
            log.warn("嵌入维度配置过小已上调：配置={}, 实际上调为={}（维度过小时桶碰撞会主导相似度）",
                configured, MIN_DIMENSION);
            configured = MIN_DIMENSION;
        }
        this.dimension = configured;
    }

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        if (text == null || text.isBlank()) {
            // 全零向量：与任何向量的余弦都是 0。调用方按"没有向量"处理，而不是当作文本相似
            return vector;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        int i = 0;
        StringBuilder word = new StringBuilder();
        while (i < lower.length()) {
            char c = lower.charAt(i);
            if (isCjk(c)) {
                flushWord(word, vector);
                addToken(vector, String.valueOf(c));
                if (i + 1 < lower.length() && isCjk(lower.charAt(i + 1))) {
                    addToken(vector, "" + c + lower.charAt(i + 1));
                }
                i++;
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                word.append(c);
                i++;
                continue;
            }
            flushWord(word, vector);
            i++;
        }
        flushWord(word, vector);
        return normalize(vector);
    }

    /**
     * 刷出一个拉丁词（长度 1 也收，因为型号/量词常常是单字母+数字）。
     *
     * @param word   词缓冲
     * @param vector 向量
     */
    private void flushWord(StringBuilder word, float[] vector) {
        if (word.length() == 0) {
            return;
        }
        addToken(vector, word.toString());
        word.setLength(0);
    }

    /**
     * 把一个词投放到向量里。
     *
     * @param vector 向量
     * @param token  词
     */
    private void addToken(float[] vector, String token) {
        long hash = fnv1a(token);
        int bucket = (int) Math.floorMod(hash, (long) dimension);
        // 取哈希的另一位当符号：碰撞时正负相抵，避免高频词统治整个向量
        float sign = ((hash >>> 33) & 1L) == 0L ? 1.0f : -1.0f;
        vector[bucket] += sign;
    }

    /**
     * L2 归一化。
     *
     * @param vector 向量（原地修改）
     * @return 同一数组
     */
    private static float[] normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += (double) value * value;
        }
        if (sum <= 0) {
            return vector;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }

    /**
     * FNV-1a 64 位哈希。
     *
     * @param token 词
     * @return 哈希值
     */
    static long fnv1a(String token) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < token.length(); i++) {
            hash ^= token.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /**
     * 是否中日韩字符（含扩展 A 与常见标点区）。
     *
     * @param c 字符
     * @return 是 CJK 返回 true
     */
    static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)      // 基本区
            || (c >= 0x3400 && c <= 0x4DBF)      // 扩展 A
            || (c >= 0xF900 && c <= 0xFAFF)      // 兼容表意
            || (c >= 0x3040 && c <= 0x30FF);     // 日文假名
    }

}
