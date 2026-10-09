package org.dromara.aigov.agent.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 沙箱运行登记请求（把宿主侧执行器的 {@code result.json} 原文登记为门槛证据）。
 *
 * <p><b>为什么只收原文、不收"你自己填的退出码"</b>：证据的可信度来自"登记的与执行器吐出来的
 * 是同一份东西"。若让调用方分别填 image/exitCode/network，他就能填一份库里没有任何原文
 * 支撑的结论——那又回到了"只凭声明"。因此接口只收 {@code resultJson} 原文，
 * 服务端自己解析并按原文实算 SHA-256 留存。</p>
 *
 * <p>{@code jobId} 单独收（而不是只从原文里取）是为了让"作业 → 登记"这条线在请求里可见，
 * 且服务端会校验它与原文里的 jobId 一致：防止把 A 作业的结果登记到 B 作业名下。</p>
 *
 * @author ai-gov
 */
@Data
public class AigSandboxRunRecordBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对象类型（AGENT_VERSION/SKILL_VERSION/PACKAGE_VERSION）
     */
    @NotBlank(message = "对象类型不能为空")
    @Size(max = 32, message = "对象类型长度不能超过 32")
    private String targetType;

    /**
     * 对象版本ID
     */
    @NotNull(message = "对象版本ID不能为空")
    private Long targetVersionId;

    /**
     * 作业ID（应与 result.json 里的 jobId 一致）
     */
    @NotBlank(message = "作业ID不能为空")
    @Size(max = 128, message = "作业ID长度不能超过 128")
    private String jobId;

    /**
     * Agent 编码（可空；来自作业请求）
     */
    @Size(max = 64, message = "Agent 编码长度不能超过 64")
    private String agentCode;

    /**
     * 执行器输出的 result.json 原文（**一字不改**）
     */
    @NotBlank(message = "result.json 原文不能为空")
    private String resultJson;

}
