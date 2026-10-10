package org.dromara.aigov.studio.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 草稿回滚入参。
 *
 * <p>两个版本号都必须带：{@code targetRevisionNo} 是"要回到哪一版"，
 * {@code expectedRevision} 是"我以为现在停在哪一版"。少后者就会把别人刚提交的修订一起回滚掉。</p>
 *
 * @author ai-gov
 */
@Data
public class AigStudioDraftRollbackBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标修订号（历史里的那一版）
     */
    @NotNull(message = "目标修订号不能为空")
    private Integer targetRevisionNo;

    /**
     * 期望的当前修订号（乐观锁）
     */
    @NotNull(message = "期望修订号不能为空（否则会覆盖别人的修改）")
    private Integer expectedRevision;

}
