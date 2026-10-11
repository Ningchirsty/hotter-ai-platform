package org.dromara.aigov.workspace.portal.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 设置默认岗位入参（主文档线增量 5）。
 *
 * <p>{@code roleCode} 留空表示**清空**默认岗位（不是"不改"）——否则用户没有办法取消默认，
 * 只能换一个，而"我不想有默认"是合理诉求。</p>
 *
 * @author ai-gov
 */
@Data
public class AigPortalDefaultRoleBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位编码；留空表示清空默认岗位
     */
    @Size(max = 80, message = "岗位编码长度不能超过 80")
    private String roleCode;

}
