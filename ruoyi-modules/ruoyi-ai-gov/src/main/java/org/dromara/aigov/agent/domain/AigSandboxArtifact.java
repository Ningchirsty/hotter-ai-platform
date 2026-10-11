package org.dromara.aigov.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 沙箱产物账本 {@code aig_sandbox_artifact}（ADR-016：产物进平台）。
 *
 * <p><b>为什么新开一张表、不复用 {@code aig_task_artifact}</b>：一次沙箱作业不是任务，
 * {@code task_id} 会长期为空；而"空"在账本里是最糟的一种值——它看起来像"忘了填"，
 * 于是后来者会去补一个其实不存在的关联。ADR-016 已裁定新开本表（2026-10-10）。</p>
 *
 * <p><b>为什么不被 {@code BaseEntity} 收编</b>：同 {@code aig_sandbox_run}——
 * 这是<b>证据</b>，不是可编辑的业务数据。带 {@code del_flag}/{@code update_*} 会误导后来者
 * 以为可以改、可以删。</p>
 *
 * <p><b>元数据在库、字节在对象存储</b>：这里存的是"这次作业产出了什么、它的摘要是什么、
 * 字节存在哪个键下"。{@link #objectKey} <b>由服务端按 sha256 生成</b>，
 * 绝不用上传方的 {@link #path} 去拼——路径穿越因此没有落点。</p>
 *
 * @author ai-gov
 */
@Data
@TableName("aig_sandbox_artifact")
public class AigSandboxArtifact implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产物记录ID
     */
    @TableId(value = "artifact_id")
    private Long artifactId;

    /**
     * 所属沙箱运行记录ID（{@code aig_sandbox_run.sandbox_run_id}）
     */
    private Long sandboxRunId;

    /**
     * 产物相对路径（与 result.json 里声明的一致）
     */
    private String path;

    /**
     * 字节数（服务端实算，必须与账本声明一致）
     */
    private Long bytes;

    /**
     * SHA-256（服务端实算，必须与账本声明一致）
     */
    private String sha256;

    /**
     * 对象存储键（服务端按 sha256 生成，不使用上传方 path）
     */
    private String objectKey;

    /**
     * 上传方声明的类型（<b>仅记录</b>；下载时一律 octet-stream，绝不回显）
     */
    private String contentType;

    /**
     * 登记人ID
     */
    private Long uploadedBy;

    /**
     * 登记时间
     */
    private LocalDateTime createTime;

}
