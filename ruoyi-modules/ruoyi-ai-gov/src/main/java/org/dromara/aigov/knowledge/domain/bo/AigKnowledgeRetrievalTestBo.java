package org.dromara.aigov.knowledge.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 知识检索测试入参（设计 §9.2 {@code POST /aigov/knowledge/retrieval-tests}）。
 *
 * <p><b>只检索、不生成回答</b>：用于区分"知识没检索到"与"模型答不好"——两者都表现为
 * "回答不对"，但修法完全不同。</p>
 *
 * <p><b>授权范围由调用方给出（这是维护测试入口）</b>：本入口要求
 * {@code aig:knowledge:retrieval:test} 权限，供知识维护人验证某个集合的检索效果。
 * 正式执行路径的授权必须由服务端从绑定解析（设计 §7.4），不接受客户端传入——两者边界不同，
 * 不要把这个测试入口当成执行入口。</p>
 *
 * @author ai-gov
 */
@Data
public class AigKnowledgeRetrievalTestBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 查询文本
     */
    @NotBlank(message = "查询不能为空")
    @Size(max = 500, message = "查询长度不能超过 500")
    private String query;

    /**
     * 允许的集合编码（授权范围）
     */
    private List<String> collectionCodes;

    /**
     * 允许的文档版本ID（可空）
     */
    private List<Long> versionIds;

    /**
     * 返回片段上限（可空=默认 5，硬上限 20）
     */
    private Integer maxChunks;

}
