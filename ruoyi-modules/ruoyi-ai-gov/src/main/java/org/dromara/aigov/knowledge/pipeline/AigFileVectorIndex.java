package org.dromara.aigov.knowledge.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.knowledge.config.AigKnowledgePipelineProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 文件向量索引（{@value #ENGINE_ID}）：<b>内部可部署的单节点基线</b>。
 *
 * <h3>为什么先给文件实现</h3>
 * <p>附件把向量存储交给评测决定。在那之前，链路需要"真的写进去、真的搜得出来"才能做评测；
 * 一个"占位实现"会让评测拿不到任何数据。文件实现零外部依赖、离线可跑，是唯一能立刻满足
 * "内部可部署"且不改变部署形态的选项。</p>
 *
 * <h3>它的边界（必须写在代码里，也必须写进运维口径）</h3>
 * <ul>
 *     <li><b>单节点</b>：索引在本地磁盘，多副本部署时每个实例只看得见自己写的文件。多副本上线前
 *         必须换成共享实现（{@code aigov.knowledge.pipeline.vector-index-dir} 指向共享卷只是权宜，
 *         并发写同一目录没有协调）；</li>
 *     <li><b>全量扫描</b>：检索是"把候选版本的向量全读进来算余弦"。版本数/分段数上到十万级就会慢，
 *         这也是"评测后再定"的一部分；</li>
 *     <li><b>不做增量</b>：写入按版本整体替换（见 {@link #replace}），简单且不会留下"删不掉的旧向量"。</li>
 * </ul>
 *
 * <h3>为什么整版本替换，而不是按条目增删</h3>
 * <p>重建索引时"漏删一条"的后果是：用户按原文删掉的段落，仍能被检索并引用出来——那是内容泄漏级别的
 * 错误。整版本替换把"删"变成一个原子动作（写临时文件再 move），不存在"删了一半"的中间态。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
public class AigFileVectorIndex implements KnowledgeVectorIndexPort {

    /**
     * 引擎 ID。
     */
    public static final String ENGINE_ID = "builtin-file-cosine-v1";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final Path directory;

    /**
     * 构造。
     *
     * @param properties 管道参数（取 {@code vector-index-dir}）
     */
    public AigFileVectorIndex(AigKnowledgePipelineProperties properties) {
        this.directory = Paths.get(properties.effectiveVectorIndexDir());
        log.info("知识向量索引目录: {}（单节点基线实现；多副本部署前必须按评测结论换成共享存储）",
            this.directory.toAbsolutePath());
    }

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public int replace(Long versionId, List<VectorEntry> entries) {
        if (versionId == null) {
            throw new AigKnowledgePipelineException(AigKnowledgePipelineException.INDEX_FAILED,
                "版本ID不能为空，无法写向量索引");
        }
        if (entries == null || entries.isEmpty()) {
            return remove(versionId);
        }
        Integer dimension = null;
        for (VectorEntry entry : entries) {
            if (entry.chunkId() == null || entry.vector() == null || entry.vector().length == 0) {
                throw new AigKnowledgePipelineException(AigKnowledgePipelineException.INDEX_FAILED,
                    "向量条目不完整（缺分段ID或向量）：versionId=" + versionId);
            }
            if (dimension == null) {
                dimension = entry.vector().length;
            } else if (dimension != entry.vector().length) {
                // 维度不一致：直接失败。补齐/截断会产出"有分数但无意义"的排序
                throw new AigKnowledgePipelineException(AigKnowledgePipelineException.INDEX_FAILED,
                    "同一版本内向量维度不一致（" + dimension + " vs " + entry.vector().length
                        + "）：versionId=" + versionId + "。请在同一批处理里使用同一个嵌入引擎。");
            }
        }
        IndexFile file = new IndexFile(engineId(), dimension, new ArrayList<>());
        for (VectorEntry entry : entries) {
            file.getItems().add(new IndexItem(entry.chunkId(), toList(entry.vector())));
        }
        Path target = fileFor(versionId);
        try {
            Files.createDirectories(directory);
            Path tmp = Files.createTempFile(directory, "index-" + versionId + "-", ".tmp");
            Files.writeString(tmp, MAPPER.writeValueAsString(file), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new AigKnowledgePipelineException(AigKnowledgePipelineException.INDEX_FAILED,
                "写向量索引失败：versionId=" + versionId + " dir=" + directory, e);
        }
        return file.getItems().size();
    }

    @Override
    public List<Hit> search(Collection<Long> versionIds, float[] queryVector, int topK) {
        if (versionIds == null || versionIds.isEmpty() || queryVector == null || topK <= 0) {
            return List.of();
        }
        List<Hit> hits = new ArrayList<>();
        for (Long versionId : versionIds) {
            IndexFile file = read(versionId);
            if (file == null) {
                continue;
            }
            if (file.getDim() == null || file.getDim() != queryVector.length) {
                log.warn("跳过维度不匹配的向量索引: versionId={}, indexDim={}, queryDim={}",
                    versionId, file.getDim(), queryVector.length);
                continue;
            }
            for (IndexItem item : file.getItems()) {
                if (item.getChunkId() == null || item.getV() == null || item.getV().size() != file.getDim()) {
                    continue;
                }
                double score = cosine(queryVector, item.getV());
                if (score > 0) {
                    hits.add(new Hit(item.getChunkId(), score));
                }
            }
        }
        hits.sort((left, right) -> Double.compare(right.score(), left.score()));
        return hits.size() <= topK ? hits : new ArrayList<>(hits.subList(0, topK));
    }

    @Override
    public int remove(Long versionId) {
        if (versionId == null) {
            return 0;
        }
        try {
            return Files.deleteIfExists(fileFor(versionId)) ? 1 : 0;
        } catch (IOException e) {
            throw new AigKnowledgePipelineException(AigKnowledgePipelineException.INDEX_FAILED,
                "删除向量索引失败：versionId=" + versionId, e);
        }
    }

    /**
     * 读取某版本的索引文件。
     *
     * @param versionId 版本ID
     * @return 索引；不存在返回 null
     */
    private IndexFile read(Long versionId) {
        Path path = fileFor(versionId);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            IndexFile file = MAPPER.readValue(Files.readString(path, StandardCharsets.UTF_8), IndexFile.class);
            if (file.getItems() == null) {
                file.setItems(new ArrayList<>());
            }
            return file;
        } catch (Exception e) {
            // 索引损坏不能当成"没有向量"：那会让检索静默退化成关键字检索而没人知道
            throw new AigKnowledgePipelineException(AigKnowledgePipelineException.INDEX_FAILED,
                "向量索引文件损坏：versionId=" + versionId + " path=" + path
                    + "。请对该版本重新执行索引（重建会整体覆盖）。", e);
        }
    }

    /**
     * 某版本的索引文件路径。
     *
     * @param versionId 版本ID
     * @return 路径
     */
    private Path fileFor(Long versionId) {
        return directory.resolve(versionId + ".json");
    }

    /**
     * 余弦相似度。
     *
     * @param query    查询向量（L2 归一化后的）
     * @param indexed  索引向量
     * @return 相似度；任一为零向量返回 0
     */
    static double cosine(float[] query, List<Float> indexed) {
        double dot = 0;
        double queryNorm = 0;
        double indexedNorm = 0;
        for (int i = 0; i < query.length; i++) {
            double left = query[i];
            double right = indexed.get(i);
            dot += left * right;
            queryNorm += left * left;
            indexedNorm += right * right;
        }
        if (queryNorm <= 0 || indexedNorm <= 0) {
            return 0;
        }
        return dot / (Math.sqrt(queryNorm) * Math.sqrt(indexedNorm));
    }

    private static List<Float> toList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    /**
     * 索引文件（JSON 结构，便于运维直接看"这个版本到底有没有向量"）。
     */
    @lombok.Data
    public static class IndexFile {

        /**
         * 产出向量的嵌入引擎。
         */
        private String engine;

        /**
         * 向量维度。
         */
        private Integer dim;

        /**
         * 条目。
         */
        private List<IndexItem> items = new ArrayList<>();

        /**
         * 构造。
         */
        public IndexFile() {
        }

        /**
         * 构造。
         *
         * @param engine 引擎
         * @param dim    维度
         * @param items  条目
         */
        public IndexFile(String engine, Integer dim, List<IndexItem> items) {
            this.engine = engine;
            this.dim = dim;
            this.items = items == null ? new ArrayList<>() : items;
        }

    }

    /**
     * 索引条目。
     */
    @lombok.Data
    public static class IndexItem {

        /**
         * 分段ID。
         */
        private Long chunkId;

        /**
         * 向量。
         */
        private List<Float> v = Collections.emptyList();

        /**
         * 构造。
         */
        public IndexItem() {
        }

        /**
         * 构造。
         *
         * @param chunkId 分段ID
         * @param v       向量
         */
        public IndexItem(Long chunkId, List<Float> v) {
            this.chunkId = chunkId;
            this.v = v;
        }

    }

}
