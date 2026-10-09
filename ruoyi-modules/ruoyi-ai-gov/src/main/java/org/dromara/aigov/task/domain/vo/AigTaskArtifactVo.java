package org.dromara.aigov.task.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.task.domain.AigTaskArtifact;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 制品账本视图。
 *
 * <p><b>{@code hashVerified} 与 {@code hashVerifiedLabel} 必须一起给出去</b>：
 * 页面上只显示一行 sha256，读的人自然会以为「平台验过了」。标签写清
 * 「声明值（平台未回读重算）」才能让这个判断不依赖读者是否看过 DDL 注释。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigTaskArtifact.class)
public class AigTaskArtifactVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long artifactId;

    private Long taskId;

    private Integer attemptNo;

    private Long resultId;

    private String artifactType;

    private String mimeType;

    private Long sizeBytes;

    private String sha256;

    private String storageRef;

    /**
     * 平台是否回读重算过哈希（{@code Y}/{@code N}）
     */
    private String hashVerified;

    /**
     * 哈希来源的可读说明（「平台回读重算」/「生产方声明值（平台未回读重算）」）
     */
    private String hashVerifiedLabel;

    private String validationStatus;

    /**
     * 校验结论描述（通过/被拒）
     */
    private String validationStatusLabel;

    private String validationDetail;

    private LocalDateTime createTime;

    private String remark;

}
