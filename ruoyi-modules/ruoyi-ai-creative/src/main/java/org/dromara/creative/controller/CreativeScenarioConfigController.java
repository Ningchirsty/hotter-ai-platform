package org.dromara.creative.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.DpDeliveryType;
import org.dromara.creative.domain.DpModuleDefinition;
import org.dromara.creative.domain.DpProjectModule;
import org.dromara.creative.domain.DpOutputSpec;
import org.dromara.creative.domain.vo.ProjectStepStateVo;
import org.dromara.creative.domain.DpScenarioProfile;
import org.dromara.creative.domain.DpScenarioStep;
import org.dromara.creative.domain.DpWorkspaceSchema;
import org.dromara.creative.service.ICreativeModuleService;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场景配置层·只读接口（V0.2 B1，文档 §36 的 `/creative/v2/*` 配置部分）。
 *
 * <p>为什么单开 `/creative/v2`：现有的 `/creative/*` 是"详情页专用流程"的接口（阶段机驱动），
 * 而这一组是**场景配置的读接口**，语义不同、以后会被工作台运行时（D 阶段）大量调用，
 * 放在 v2 前缀下便于区分，也不影响既有前端（它现在还不调这些接口）。</p>
 *
 * <p>权限：复用 {@link CreativeConstants#PERM_PROJECT_LIST}——配置读是"看创意项目"这一档能力，
 * 不新造权限点，避免漏配角色导致 403。</p>
 *
 * @author creative
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/creative/v2")
public class CreativeScenarioConfigController {

    private final ICreativeScenarioConfigService scenarioConfigService;

    /**
     * 模块引擎（R21，文档 §18/§21）：模块库与项目模块计划。
     */
    private final ICreativeModuleService moduleService;

    /**
     * 全部启用的交付类型。
     *
     * @return 交付类型列表
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_LIST)
    @GetMapping("/delivery-types")
    public R<List<DpDeliveryType>> deliveryTypes() {
        return R.ok(scenarioConfigService.listDeliveryTypes());
    }

    /**
     * 按编码查交付类型（支持别名）。
     *
     * @param code 交付类型编码或别名
     * @return 交付类型；查不到返回 null
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_LIST)
    @GetMapping("/delivery-types/{code}")
    public R<DpDeliveryType> deliveryType(@PathVariable("code") String code) {
        return R.ok(scenarioConfigService.getDeliveryType(code));
    }

    /**
     * 取某交付类型的场景档案。
     *
     * @param deliveryType 交付类型编码或别名
     * @return 场景档案；没有返回 null
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_LIST)
    @GetMapping("/scenarios/{deliveryType}")
    public R<DpScenarioProfile> scenario(@PathVariable("deliveryType") String deliveryType) {
        return R.ok(scenarioConfigService.getScenario(deliveryType));
    }

    /**
     * 取某交付类型的流程步骤。
     *
     * @param deliveryType 交付类型编码或别名
     * @return 步骤列表
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_LIST)
    @GetMapping("/scenarios/{deliveryType}/steps")
    public R<List<DpScenarioStep>> steps(@PathVariable("deliveryType") String deliveryType) {
        return R.ok(scenarioConfigService.listSteps(deliveryType));
    }

    /**
     * 取某交付类型的工作台装配。
     *
     * @param deliveryType 交付类型编码或别名
     * @return 工作台装配；没有返回 null
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_LIST)
    @GetMapping("/scenarios/{deliveryType}/workspace")
    public R<DpWorkspaceSchema> workspace(@PathVariable("deliveryType") String deliveryType) {
        return R.ok(scenarioConfigService.getWorkspace(deliveryType));
    }

    /**
     * 取某交付类型的输出规格（默认规格排最前）。
     *
     * @param deliveryType 交付类型编码或别名
     * @return 输出规格列表
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_LIST)
    @GetMapping("/scenarios/{deliveryType}/output-specs")
    public R<List<DpOutputSpec>> outputSpecs(@PathVariable("deliveryType") String deliveryType) {
        return R.ok(scenarioConfigService.listOutputSpecs(deliveryType));
    }

    /**
     * 取某项目的「配置步骤 + 步骤状态」（V0.2 D2，文档 §36）。
     *
     * <p>只读：没有持久化行的项目按**当前阶段**推导投影，查询本身不写库。</p>
     *
     * @param taskId 项目ID
     * @return 步骤状态列表
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_LIST)
    @GetMapping("/projects/{taskId}/steps")
    public R<List<ProjectStepStateVo>> projectSteps(@PathVariable("taskId") Long taskId) {
        return R.ok(scenarioConfigService.listProjectSteps(taskId));
    }

    /**
     * 取某交付类型的模块库（V0.2 R21，文档 §18/§20）。
     *
     * <p>默认骨架（{@code defaultSelected=1}）排在前面，其余是可选模块库；
     * `screenType/productLockLevel/shot` 与屏骨架契约同名字段一一对应，因此"模块 → 屏"是可核对的。</p>
     *
     * @param deliveryType 交付类型编码或别名
     * @return 模块定义列表
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_LIST)
    @GetMapping("/modules")
    public R<List<DpModuleDefinition>> modules(
        @RequestParam(value = "deliveryType", required = false) String deliveryType) {
        return R.ok(moduleService.listDefinitions(deliveryType));
    }

    /**
     * 取某项目的模块计划（V0.2 R21，文档 §21）。
     *
     * <p>只读：不初始化、不写库。项目还没有模块计划时返回空列表——
     * 此时分镜按屏骨架契约文件生成（见分镜服务里的回落与日志）。</p>
     *
     * @param taskId 项目ID
     * @return 模块计划（按 sortNo）
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_LIST)
    @GetMapping("/projects/{taskId}/modules")
    public R<List<DpProjectModule>> projectModules(@PathVariable("taskId") Long taskId) {
        return R.ok(moduleService.listProjectModules(taskId));
    }
}
