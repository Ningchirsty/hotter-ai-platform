package org.dromara.aigov.workspace.portal.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * 门户里的岗位首页（主文档线增量 2）。
 *
 * <p>{@link #actions} 是<b>服务端过滤后</b>的结果：只有启用中的卡片、且分类能在清单里找到。
 * 过滤放在服务端而不是让前端筛，理由很实际——前端筛选意味着"不该看到的也发到了浏览器"，
 * 而岗位包里可能有面向特定组织/品牌的配置。所以"不该给的字段根本不发"。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AigPortalRoleHomeVo extends AigPortalRoleVo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 服务端过滤后的卡片清单（按分类/排序）
     */
    private List<AigPortalActionVo> actions;

}
