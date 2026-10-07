package org.dromara.aigov.task.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.task.domain.AigTaskSnapshot;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 输入快照视图（详情页展示）。
 *
 * <p>刻意把 {@link #snapshotJson} 原文与 {@link #snapshotHash} 一起给出：
 * 只有哈希没有原文，读者无法自行验证；只有原文没有哈希，
 * 就无法回答「执行期间它有没有被改过」。两者同时出现，这个视图才可自证。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigTaskSnapshot.class)
public class AigTaskSnapshotVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long snapshotId;

    private Long taskId;

    private Integer snapshotVersion;

    /**
     * 冻结内容原文
     */
    private String snapshotJson;

    /**
     * 内容 SHA-256（对原文字节计算）
     */
    private String snapshotHash;

    private String dataLevel;

    private String allowExternal;

    private BigDecimal budgetAmount;

    private String negativeConstraints;

    private LocalDateTime frozenAt;

    private Long createBy;

    private LocalDateTime createTime;

}
