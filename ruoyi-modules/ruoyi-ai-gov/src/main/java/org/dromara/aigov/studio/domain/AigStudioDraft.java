package org.dromara.aigov.studio.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * Agent Studio 训练草稿 {@code aig_studio_draft}（专题 C §C2.1、§C8）。
 *
 * <p><b>它为什么独立于 {@code aig_agent_version}</b>：正式版本不可变，而训练是"反复改、反复试、
 * 随时回退"。把每次编辑写成对已发布版本的覆盖，等于让线上能力随一次试改漂移且回不去。</p>
 *
 * <p><b>为什么 {@link #latestRevision} 不用 {@code @Version}</b>：注解式的乐观锁会在**任何**更新时自增，
 * 而"修订号"只应在**内容变化**时前进（改状态、记 agentVersionId 都不算新修订）。
 * 因此这里用普通列 + 服务层显式 CAS（{@code where latest_revision = expected}），
 * 语义比"每次写库都 +1"清楚。</p>
 *
 * <p><b>「未发布改动」的判据</b>：{@code contentHash != lastPublishedHash}（服务端事实），
 * 不是前端布尔量——见 {@code AigStudioContentHasher}。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_studio_draft")
public class AigStudioDraft extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 训练草稿ID
     */
    @TableId(value = "draft_id")
    private Long draftId;

    /**
     * 关联的 Agent 定义（从零创建时为空）
     */
    private Long agentId;

    /**
     * 训练对象编码（跨版本稳定；从零创建时由建设人员指定）
     */
    private String agentCode;

    /**
     * 归属组织（部门ID；空=集团级）
     */
    private Long orgId;

    /**
     * 责任人（授权建设人员）
     */
    private Long ownerId;

    /**
     * 最新修订号（0=还没保存过内容）；编辑必须带期望值做 CAS
     */
    private Integer latestRevision;

    /**
     * 草稿内容（结构化 JSON，{@code AigStudioDraftContent} 的序列化）
     */
    private String contentJson;

    /**
     * 内容哈希（规范化 JSON 的 sha256）
     */
    private String contentHash;

    /**
     * 最近一次已提交/发布时的内容哈希（与 contentHash 不等即有未发布改动；为空=从未提交）
     */
    private String lastPublishedHash;

    /**
     * 最近一次由本草稿提交产生的 Agent 版本（{@code release_status=DRAFT}）
     */
    private Long agentVersionId;

    /**
     * 草稿状态（{@code AigStudioDraftStatusEnum}）
     */
    private String status;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
