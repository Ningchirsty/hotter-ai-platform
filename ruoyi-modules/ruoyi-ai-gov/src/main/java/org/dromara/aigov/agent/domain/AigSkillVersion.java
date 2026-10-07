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
 * Skill 版本 aig_skill_version（设计 §5.3 的工具策略 + §5.4 发布状态机）。
 *
 * <p><b>{@link #toolPolicyJson} 是 Skill 的安全边界</b>：设计 §5.1 明确
 * 「Agent 调用 Skill、工具或 Provider 时，始终受 Tool Policy 和数据策略限制」。
 * 因此工具策略放在 <b>Skill 版本</b>上（原子能力自己声明它能碰什么），
 * 而不是只在 Agent 上声明——否则一个被三个 Agent 复用的 Skill，
 * 权限会取决于谁调用它，边界就说不清了。</p>
 *
 * <p><b>越权的写法是「声明了但没人读」</b>：设计 §6.2 把「不能限制的任意代码执行」
 * 列为拒绝项。首期只有声明式 Package（无代码），因此本列当前的作用是
 * <b>登记 + 在安装扫描时校验其自洽性</b>（例如禁止工具里出现它自己声明需要的工具）；
 * 运行时拦截要在有可执行 Skill 之后才有意义——那不属于首期范围。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_skill_version")
public class AigSkillVersion extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Skill 版本ID
     */
    @TableId(value = "skill_version_id")
    private Long skillVersionId;

    /**
     * 所属 Skill
     */
    private Long skillId;

    /**
     * 版本号（同 Skill 内唯一）
     */
    private String version;

    /**
     * 发布状态机（{@code AigReleaseStatusEnum}）
     */
    private String releaseStatus;

    /**
     * 发布通道/可见范围（{@code AigReleaseChannelEnum}）
     */
    private String releaseChannel;

    /**
     * Skill 配置
     */
    private String configJson;

    /**
     * 工具策略（允许/禁止工具与调用前置条件）
     */
    private String toolPolicyJson;

    /**
     * 需要的 Provider 能力编码
     */
    private String providerCapability;

    /**
     * 是否允许外部调用（Y/N）
     */
    private String allowExternal;

    /**
     * 输入 Schema
     */
    private String inputSchema;

    /**
     * 输出 Schema
     */
    private String outputSchema;

    /**
     * 人工批准人
     */
    private Long approvedBy;

    /**
     * 人工批准时间
     */
    private LocalDateTime approvedAt;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 版本说明
     */
    private String remark;

}
