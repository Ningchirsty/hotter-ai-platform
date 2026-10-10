package org.dromara.aigov.workspace.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.studio.helper.AigStudioActorProvider;
import org.dromara.aigov.workspace.domain.bo.AigRolePackageDisableBo;
import org.dromara.aigov.workspace.domain.bo.AigRolePackageQueryBo;
import org.dromara.aigov.workspace.domain.bo.AigRolePackageSaveBo;
import org.dromara.aigov.workspace.domain.bo.AigRoleReleaseAdvanceBo;
import org.dromara.aigov.workspace.domain.vo.AigRolePackageValidateVo;
import org.dromara.aigov.workspace.domain.vo.AigRoleVersionDetailVo;
import org.dromara.aigov.workspace.domain.vo.AigRoleVersionVo;
import org.dromara.aigov.workspace.enums.AigRoleReleaseStatusEnum;
import org.dromara.aigov.workspace.service.IAigRolePackageService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 岗位包管理接口（主文档线增量 1b）。
 *
 * <h3>发布与停用为什么是两个端点</h3>
 * <p>权限点分开了（{@code aig:role:publish} / {@code aig:role:disable}），
 * 而 {@code @SaCheckPermission} 在编译期是静态的——一个"给我目标状态"的端点没法既是发布
 * 又是停用。若合成一个端点，就必然要用其中一个权限覆盖两种动作，
 * 于是"能发布"就等于"能撤下问题版本"，或者反过来。两个端点让权限与动作一一对应。</p>
 *
 * <p>能否流转本身由 {@code AigRoleReleaseTransition} 的边表判定：从 DRAFT 不能直接
 * 到 PUBLISHED，同状态之间不算流转。这两个端点只决定"谁能按"。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/roles")
public class AigRolePackageController extends BaseController {

    private final IAigRolePackageService rolePackageService;

    /**
     * 操作者解析：与训练台复用同一个（语义相同——人工工作台、无系统身份）。
     */
    private final AigStudioActorProvider actorProvider;

    /**
     * 分页查询岗位包版本。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_ROLE_PACKAGE_LIST)
    @GetMapping("/list")
    public R<PageResult<AigRoleVersionVo>> list(AigRolePackageQueryBo bo, PageQuery pageQuery) {
        return R.ok(rolePackageService.queryPage(bo, pageQuery));
    }

    /**
     * 某个岗位的全部版本。
     *
     * @param roleId 岗位定义ID
     * @return 版本清单
     */
    @SaCheckPermission(AigConstants.PERM_ROLE_PACKAGE_LIST)
    @GetMapping("/{roleId:\\d+}/versions")
    public R<List<AigRoleVersionVo>> versions(@NotNull(message = "岗位ID不能为空")
                                              @PathVariable Long roleId) {
        return R.ok(rolePackageService.listVersions(roleId));
    }

    /**
     * 版本详情（含卡片、清单原文与校验结论）。
     *
     * @param roleVersionId 岗位版本ID
     * @return 详情
     */
    @SaCheckPermission(AigConstants.PERM_ROLE_PACKAGE_QUERY)
    @GetMapping("/version/{roleVersionId:\\d+}")
    public R<AigRoleVersionDetailVo> detail(@NotNull(message = "岗位版本ID不能为空")
                                            @PathVariable Long roleVersionId) {
        return R.ok(rolePackageService.getVersionDetail(roleVersionId));
    }

    /**
     * 预检一份"还没入库"的岗位包（只读）。
     *
     * @param bo 草稿入参
     * @return 预检结论
     */
    @SaCheckPermission(AigConstants.PERM_ROLE_PACKAGE_VALIDATE)
    @PostMapping("/validate")
    public R<AigRolePackageValidateVo> validate(@RequestBody @Validated AigRolePackageSaveBo bo) {
        return R.ok(rolePackageService.validate(bo));
    }

    /**
     * 校验库里已存在的那一份版本（发布前必做）。
     *
     * @param roleVersionId 岗位版本ID
     * @return 预检结论
     */
    @SaCheckPermission(AigConstants.PERM_ROLE_PACKAGE_VALIDATE)
    @PostMapping("/version/{roleVersionId:\\d+}/validate")
    public R<AigRolePackageValidateVo> validateStored(@NotNull(message = "岗位版本ID不能为空")
                                                      @PathVariable Long roleVersionId) {
        return R.ok(rolePackageService.validateStored(roleVersionId));
    }

    /**
     * 保存岗位包草稿（新建岗位/新版本，或覆盖 DRAFT 版本）。
     *
     * @param bo 草稿入参
     * @return 保存后的详情
     */
    @SaCheckPermission(AigConstants.PERM_ROLE_PACKAGE_EDIT)
    @RepeatSubmit
    @PostMapping
    public R<AigRoleVersionDetailVo> save(@RequestBody @Validated AigRolePackageSaveBo bo) {
        return R.ok(rolePackageService.saveDraft(bo, requireActor()));
    }

    /**
     * 覆盖保存一个 DRAFT 版本（等价入口，语义更明确）。
     *
     * @param roleVersionId 岗位版本ID
     * @param bo            草稿入参（路径参数覆盖请求体里的 roleVersionId）
     * @return 保存后的详情
     */
    @SaCheckPermission(AigConstants.PERM_ROLE_PACKAGE_EDIT)
    @PutMapping("/version/{roleVersionId:\\d+}")
    public R<AigRoleVersionDetailVo> update(@NotNull(message = "岗位版本ID不能为空")
                                            @PathVariable Long roleVersionId,
                                            @RequestBody @Validated AigRolePackageSaveBo bo) {
        // 以路径为准：两个位置都给时若信请求体，就会出现"改 A 却提交到 B"的越权面
        bo.setRoleVersionId(roleVersionId);
        return R.ok(rolePackageService.saveDraft(bo, requireActor()));
    }

    /**
     * 发布流转（DRAFT→TESTING→PUBLISHED，以及已停用版本的重新启用）。
     *
     * @param roleVersionId 岗位版本ID
     * @param bo            入参（目标状态必须是 TESTING 或 PUBLISHED）
     * @return 流转后的详情
     */
    @SaCheckPermission(AigConstants.PERM_ROLE_PACKAGE_PUBLISH)
    @RepeatSubmit
    @PostMapping("/version/{roleVersionId:\\d+}/publish")
    public R<AigRoleVersionDetailVo> publish(@NotNull(message = "岗位版本ID不能为空")
                                             @PathVariable Long roleVersionId,
                                             @RequestBody @Validated AigRoleReleaseAdvanceBo bo) {
        AigRoleReleaseStatusEnum target = AigRoleReleaseStatusEnum.find(bo.getTargetStatus());
        if (target == null
            || (target != AigRoleReleaseStatusEnum.TESTING && target != AigRoleReleaseStatusEnum.PUBLISHED)) {
            throw new ServiceException("发布接口只接受 TESTING 或 PUBLISHED："
                + "停用请走停用接口（那是另一个权限点，不能靠改这个参数绕过）");
        }
        return R.ok(rolePackageService.advance(roleVersionId, target.getCode(), bo.getRemark(),
            requireActor()));
    }

    /**
     * 停用版本（禁止新用户获得该版本；可重新启用）。
     *
     * @param roleVersionId 岗位版本ID
     * @param bo            入参（说明）
     * @return 流转后的详情
     */
    @SaCheckPermission(AigConstants.PERM_ROLE_PACKAGE_DISABLE)
    @RepeatSubmit
    @PostMapping("/version/{roleVersionId:\\d+}/disable")
    public R<AigRoleVersionDetailVo> disable(@NotNull(message = "岗位版本ID不能为空")
                                             @PathVariable Long roleVersionId,
                                             @RequestBody(required = false) @Validated AigRolePackageDisableBo bo) {
        String remark = bo == null ? null : bo.getRemark();
        return R.ok(rolePackageService.advance(roleVersionId,
            AigRoleReleaseStatusEnum.DISABLED.getCode(), remark, requireActor()));
    }

    /**
     * 取当前操作者；取不到就拒绝。
     *
     * @return 用户ID
     */
    private Long requireActor() {
        Long actorId = actorProvider.currentUserId();
        if (actorId == null) {
            throw new ServiceException("岗位包管理需要登录用户：无法确定这次变更由谁发起");
        }
        return actorId;
    }

}
