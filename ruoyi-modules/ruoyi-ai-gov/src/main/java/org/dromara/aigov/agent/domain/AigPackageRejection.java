package org.dromara.aigov.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Package 注册被拒证据（{@code aig_package_rejection}，追加型账本）。
 *
 * <p><b>为什么不被 BaseEntity 收编</b>：与 {@code aig_policy_decision_log} / {@code aig_callback}
 * 同口径——这是<b>证据</b>，不是可编辑的业务数据。带上 {@code del_flag}/{@code update_*}
 * 会误导后来者以为可以改、可以删。</p>
 *
 * <p>三个拒绝点共用本表，由 {@link #rejectReason} 区分：
 * {@code MANIFEST_INVALID}（声明不合法）、{@code CHECKSUM_MISMATCH}（包体与声明不是同一份）、
 * {@code ARCHIVE_UNSAFE}（包体内容安全检查不通过）；后者还会带 {@link #hitRules}。</p>
 *
 * @author ai-gov
 */
@Data
@TableName("aig_package_rejection")
public class AigPackageRejection implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 被拒记录ID
     */
    @TableId(value = "rejection_id")
    private Long rejectionId;

    /**
     * 包编码（Manifest 无法解析时为空）
     */
    private String packageCode;

    /**
     * 包版本（同上，可能为空）
     */
    private String packageVersion;

    /**
     * 上传的文件名
     */
    private String bodyName;

    /**
     * 包体 SHA-256（服务端实算）
     */
    private String bodySha256;

    /**
     * 包体字节数
     */
    private Long bodySize;

    /**
     * 拒绝原因（MANIFEST_INVALID/CHECKSUM_MISMATCH/ARCHIVE_UNSAFE）
     */
    private String rejectReason;

    /**
     * 命中的拒绝规则码（逗号分隔）
     */
    private String hitRules;

    /**
     * 可读明细
     */
    private String detail;

    /**
     * 操作者ID（取不到时为空=系统触发）
     */
    private Long operatorId;

    /**
     * 记录时间
     */
    private LocalDateTime createTime;

}
