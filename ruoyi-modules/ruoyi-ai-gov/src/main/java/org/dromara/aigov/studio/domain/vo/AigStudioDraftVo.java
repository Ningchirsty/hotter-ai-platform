package org.dromara.aigov.studio.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.studio.domain.AigStudioDraft;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 训练草稿列表/详情视图。
 *
 * <p><b>为什么必须有 {@code @AutoMapper}</b>：{@code BaseMapperPlus.selectVoPage/selectVoList}
 * 在运行期按约定查找 {@code AigStudioDraftToAigStudioDraftVoMapper}，缺注解时列表接口会**恒 500**
 * （空表也一样）。本模块已有一条扫描全部 Mapper 的守卫测试专门盯这件事。</p>
 *
 * <p>标签字段（如草稿状态中文名）刻意**不在这里预置**：它由服务层填充，
 * 先放一个没人填的字段等于又造一个"永远为空"的列。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigStudioDraft.class)
public class AigStudioDraftVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 训练草稿ID
     */
    private Long draftId;

    /**
     * 关联的 Agent 定义
     */
    private Long agentId;

    /**
     * 训练对象编码
     */
    private String agentCode;

    /**
     * 归属组织
     */
    private Long orgId;

    /**
     * 责任人
     */
    private Long ownerId;

    /**
     * 最新修订号
     */
    private Integer latestRevision;

    /**
     * 草稿内容（结构化 JSON）
     */
    private String contentJson;

    /**
     * 内容哈希
     */
    private String contentHash;

    /**
     * 最近一次已提交时的内容哈希
     */
    private String lastPublishedHash;

    /**
     * 最近一次提交产生的 Agent 版本
     */
    private Long agentVersionId;

    /**
     * 草稿状态（{@code AigStudioDraftStatusEnum} 的 code）
     */
    private String status;

    /**
     * 草稿状态的中文描述（<b>由服务层分页映射时填充</b>；不填就是又造一个"永远为空"的字段）
     */
    private String statusLabel;

    /**
     * 是否有未提交的改动（<b>由服务层分页映射时填充</b>，判据是 contentHash != lastPublishedHash）
     */
    private Boolean unpublishedChanges;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
