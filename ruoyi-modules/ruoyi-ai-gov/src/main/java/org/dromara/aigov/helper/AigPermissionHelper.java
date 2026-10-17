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
 * <p>用途：控制 {@code api_endpoint} / {@code secretRef} 的<b>读</b>（{@code aig:model:secret:view}）
 * 与<b>写</b>（{@code aig:model:secret:edit}）。两者刻意分开——原先是同一个
 * {@code aig:model:secret}，而菜单名写的是「查看密钥引用」，于是"只被授了查看"的人实际能改凭据。</p>
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
     * 当前用户能否查看模型端点与密钥引用（**只读**口径）。
     *
     * <p>超管或拥有 {@code aig:model:secret:view} 时放行。</p>
     * <p>刻意用 {@code view} 而不是 {@code edit}：这个判断只决定"能不能看到脱敏引用/端点"，
     * 拿它来决定"能不能写密钥"就是走读里点名的那个错（菜单叫「查看」，实际能改）。</p>
     *
     * @return 是否可查看敏感配置
     */
    public boolean canViewModelSecret() {
        return LoginHelper.isSuperAdmin() || hasMenuPermission(AigConstants.PERM_MODEL_SECRET_VIEW);
    }

    /**
     * 当前用户能否写入/清除模型端点、密钥引用与明文密钥（**写**口径）。
     *
     * <p>超管或拥有 {@code aig:model:secret:edit} 时放行。</p>
     * <p>写侧**不再**以 view 为准：能看见脱敏引用不等于可以改它。写侧一旦放宽，
     * 就能把引用指向自控 KMS 或他人凭据，属于凭据外流。</p>
     *
     * @return 是否可写敏感配置
     */
    public boolean canEditModelSecret() {
        return LoginHelper.isSuperAdmin() || hasMenuPermission(AigConstants.PERM_MODEL_SECRET_EDIT);
    }

}
