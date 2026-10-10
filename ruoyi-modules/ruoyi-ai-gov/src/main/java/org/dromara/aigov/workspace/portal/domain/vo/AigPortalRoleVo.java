package org.dromara.aigov.workspace.portal.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 门户里的岗位（主文档线增量 2）。
 *
 * <p>一个岗位只出现一次：员工要的是"这个岗位现在怎么做"，不是版本列表
 * （版本管理是管理台的事）。若同一岗位有多个可见版本，服务端取<b>最新</b>的那个
 * （比较方式见 {@code AigVersionPick}：按数字段比较，不是字符串比较）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigPortalRoleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位编码
     */
    private String roleCode;

    /**
     * 岗位名称
     */
    private String roleName;

    /**
     * 岗位简介（员工看到的说明）
     */
    private String description;

    /**
     * 对外展示的版本号
     */
    private String version;

    /**
     * 分类（含各分类的卡片数）
     */
    private List<AigPortalCategoryVo> categories;

    /**
     * 可见卡片总数（0 = 这个岗位目前没有任何可用卡片）
     */
    private Integer actionCount;

}
