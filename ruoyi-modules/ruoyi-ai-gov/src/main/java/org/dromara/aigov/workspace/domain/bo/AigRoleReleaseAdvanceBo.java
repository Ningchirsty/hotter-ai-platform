package org.dromara.aigov.workspace.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 岗位包发布流转入参（主文档线增量 1b）。
 *
 * <p>目标状态<b>由调用方给出</b>，而不是"一个 publish 端点自动一路推到 PUBLISHED"：
 * 发布要能停在 TESTING（先给测试账号预览），也要能直接把 PUBLISHED 的版本停用。
 * 允许的边由 {@code AigRoleReleaseTransition} 写死的表判定，不是这里说了算。</p>
 *
 * @author ai-gov
 */
@Data
public class AigRoleReleaseAdvanceBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标发布状态（DRAFT/TESTING/PUBLISHED/DISABLED 之一）
     */
    @NotBlank(message = "目标发布状态不能为空")
    @Size(max = 24, message = "目标发布状态长度不合法")
    private String targetStatus;

    /**
     * 本次流转说明（写进 remark，便于事后回答"这个版本什么时候、为什么上架"）
     */
    @Size(max = 500, message = "流转说明长度不能超过 500")
    private String remark;

}
