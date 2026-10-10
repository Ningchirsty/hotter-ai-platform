package org.dromara.aigov.workspace.portal.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 工作台偏好（主文档线增量 5）。
 *
 * <p>只返回**偏好**本身：收藏与默认岗位。可见性仍由 {@code /roles} 那条路径决定——
 * 收藏里的岗位若某天不可见了，它会出现在这里（偏好是用户自己存的），但{%@code /roles} 不会返回它，
 * 界面以 `/roles` 为准决定展示哪些岗位卡片。</p>
 *
 * @author ai-gov
 */
@Data
public class AigPortalPrefVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 收藏的岗位编码（按收藏顺序）
     */
    private List<String> favorites;

    /**
     * 默认打开的岗位编码（可空）
     */
    private String defaultRoleCode;

}
