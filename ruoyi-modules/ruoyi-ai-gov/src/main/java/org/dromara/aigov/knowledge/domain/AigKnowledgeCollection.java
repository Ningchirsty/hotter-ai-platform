package org.dromara.aigov.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识集合 aig_knowledge_collection（设计 §8.2）。
 *
 * <p>集合是<b>授权与责任的最小单位</b>：文档属于集合，检索按集合授权。这样"同一份文件在不同
 * 业务范围不能共享授权"（设计 §6.4）才落得下来——授权给集合，而不是给文件。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_knowledge_collection")
public class AigKnowledgeCollection extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 集合ID
     */
    @TableId(value = "collection_id")
    private Long collectionId;

    /**
     * 集合编码（租户内唯一）
     */
    private String collectionCode;

    /**
     * 集合名称
     */
    private String collectionName;

    /**
     * 责任人
     */
    private Long ownerId;

    /**
     * 组织范围（可空=不限）
     */
    private String orgScope;

    /**
     * 数据等级（默认 STRICT：知识资料按企业禁区处理）
     */
    private String dataLevel;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
