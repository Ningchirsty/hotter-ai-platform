package org.dromara.aigov.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 沙箱产物下载账本 {@code aig_sandbox_artifact_download}（ADR-016 安全硬要求 ⑤）。
 *
 * <p><b>它回答的问题是安全事件的第一个问题</b>：这段不可信内容被谁取走过、什么时候、从哪个 IP。
 * 只记应用日志答不了——日志会轮转、会随容器重建丢掉，而且口径随时能被顺手改掉。</p>
 *
 * <p><b>为什么记 {@link #sha256}/{@link #bytes} 而不只记 {@code artifactId}</b>：
 * 事后要核对"当时给他的到底是哪一份字节"。产物行的摘要将来若因数据修复而变，
 * 只记 ID 就再也说不清历史。这两个值是**本次下发时**核对通过的那一份。</p>
 *
 * <p><b>为什么不被 {@code BaseEntity} 收编</b>：同 {@code aig_sandbox_run}——审计记录不是
 * 可编辑的业务数据，带 {@code del_flag}/{@code update_*} 会误导后来者以为可以改、可以删。</p>
 *
 * @author ai-gov
 */
@Data
@TableName("aig_sandbox_artifact_download")
public class AigSandboxArtifactDownload implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 下载记录ID
     */
    @TableId(value = "download_id")
    private Long downloadId;

    /**
     * 所属沙箱运行记录ID
     */
    private Long sandboxRunId;

    /**
     * 产物记录ID
     */
    private Long artifactId;

    /**
     * 产物相对路径
     */
    private String path;

    /**
     * 本次下发的 SHA-256（与账本核对通过后的值）
     */
    private String sha256;

    /**
     * 本次下发的字节数
     */
    private Long bytes;

    /**
     * 下载人ID（取自登录态）
     */
    private Long downloadedBy;

    /**
     * 来源 IP（审计用）
     */
    private String clientIp;

    /**
     * 下载时间
     */
    private LocalDateTime createTime;

}
