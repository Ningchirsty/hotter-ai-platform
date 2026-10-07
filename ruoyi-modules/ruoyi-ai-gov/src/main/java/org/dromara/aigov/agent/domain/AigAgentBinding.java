package org.dromara.aigov.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 版本绑定 aig_agent_binding（设计 §10.2 + §6.3-6 的灰度范围）。
 *
 * <p><b>为什么需要这张表而不是在版本上写几个「可见范围」字段</b>：一个候选版本要同时
 * 对<b>多个</b>品牌/部门/场景可见（灰度往往是「先给 A、B 两个品牌用」），
 * 而且这些范围会随灰度推进变化。塞进版本表就得用逗号分隔的字符串，
 * 既无法索引也无法保证一致性；独立成表后每个范围一行，可加、可停用、可限时
 * （{@link #effectiveFrom}/{@link #effectiveTo}）。</p>
 *
 * <p><b>受限通道必须有绑定，否则等于「发布了但谁都看不见」</b>：当
 * {@link #releaseChannel} 是 TESTING/BRAND/DEPT 这类受限通道时，若没有任何启用中的绑定行，
 * 这个版本在业务侧根本不可见——而它在治理台上仍然显示「已发布」。
 * 这种自相矛盾要在写入时就挡住（服务层校验），不能等到有人问「为什么选了却没反应」。</p>
 *
 * <p><b>空值的含义是「不限」，不是「都不匹配」</b>：{@code companyId/brandId/scenarioCode}
 * 为空表示该维度不参与收窄。这一点必须与代码一致，否则会出现「因为没填所以全都不可见」
 * 这种最难查的错——它看起来像权限问题，实际是数据语义问题。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_agent_binding")
public class AigAgentBinding extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定ID
     */
    @TableId(value = "binding_id")
    private Long bindingId;

    /**
     * 绑定的 Agent 版本ID
     */
    private Long agentVersionId;

    /**
     * 限定公司（空=不限）
     */
    private Long companyId;

    /**
     * 限定品牌（空=不限）
     */
    private Long brandId;

    /**
     * 限定业务场景（空=不限）
     */
    private String scenarioCode;

    /**
     * 限定角色（逗号分隔的 role_key，空=不限）
     */
    private String roleScope;

    /**
     * 知识范围限定（空=不做额外收窄）
     */
    private String knowledgeScope;

    /**
     * 绑定所属发布通道（须与版本通道一致）
     */
    private String releaseChannel;

    /**
     * 是否启用（Y/N）
     */
    private String enabled;

    /**
     * 生效开始
     */
    private LocalDateTime effectiveFrom;

    /**
     * 生效结束
     */
    private LocalDateTime effectiveTo;

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
