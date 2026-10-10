package org.dromara.aigov.workspace.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 岗位包停用入参（主文档线增量 1b）。
 *
 * <p>只有说明：目标状态是端点决定的（就是 DISABLED），不让调用方传——
 * 一个"带目标状态的停用接口"很容易被当成通用的状态修改入口用。</p>
 *
 * @author ai-gov
 */
@Data
public class AigRolePackageDisableBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 停用原因（写进 remark，便于事后回答"这个版本为什么被叫停"）
     */
    @Size(max = 500, message = "停用说明长度不能超过 500")
    private String remark;

}
