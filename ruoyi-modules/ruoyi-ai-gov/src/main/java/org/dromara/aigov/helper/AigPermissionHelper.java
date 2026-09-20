package org.dromara.aigov.helper;

import cn.hutool.core.collection.CollUtil;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.model.LoginUser;
import org.springframework.stereotype.Component;

/**
 * AI 治理层当前用户权限判断工具。
 * <p>写法与 {@code TalentScopeHelper#hasMenuPermission} 同口径：直接读
 * {@link LoginUser#getMenuPermission()}，不做二次查库。</p>
 * <p>用途：控制 {@code api_endpoint} / {@code secretRef} 是否下发（{@code aig:model:secret}）。</p>
 *
 * @author ai-gov
 */
@Component
public class AigPermissionHelper {

    /**
     * 当前用户是否拥有指定菜单权限。
     *
     * @param permission 权限标识
     * @return 是否拥有
     */
    public boolean hasMenuPermission(String permission) {
        if (StringUtils.isBlank(permission)) {
            return false;
        }
        LoginUser loginUser = LoginHelper.getLoginUser();
        return loginUser != null && CollUtil.contains(loginUser.getMenuPermission(), permission);
    }

    /**
     * 当前用户能否查看模型端点与密钥引用。
     * <p>超管或拥有 {@code aig:model:secret} 权限时放行。</p>
     *
     * @return 是否可查看敏感配置
     */
    public boolean canViewModelSecret() {
        return LoginHelper.isSuperAdmin() || hasMenuPermission(AigConstants.PERM_MODEL_SECRET);
    }

}
