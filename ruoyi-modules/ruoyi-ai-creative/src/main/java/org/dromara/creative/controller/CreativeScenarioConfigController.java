package org.dromara.creative.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.dromara.creative.domain.bo.ModuleDefinitionBo;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.DpDeliveryType;
import org.dromara.creative.domain.DpModuleDefinition;
import org.dromara.creative.domain.DpProjectModule;
import org.dromara.creative.domain.DpOutputSpec;
import org.dromara.creative.domain.bo.ProjectModulePlanBo;
import org.dromara.creative.domain.vo.ProjectModulePlanVo;
import org.dromara.creative.domain.vo.ProjectStepStateVo;
import org.dromara.creative.domain.DpScenarioProfile;
import org.dromara.creative.domain.DpScenarioStep;
import org.dromara.creative.domain.DpWorkspaceSchema;
import org.dromara.creative.service.ICreativeModuleService;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.dromara.creative.service.ICreativeStepStateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
     * 步骤状态的人为动作（R36：跳过 / 取消跳过）
     */
    private final ICreativeStepStateService stepStateService;

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
     * 跳过某一步（V0.2 R36）。
     *
     * <p><b>只对"可选且无闸门"的步骤开放</b>：必填步骤是流程要求，有闸门的步骤跳过等于绕过门禁。
     * 必须给原因（留痕：写 remark + 一条 {@code STEP_SKIPPED} 事件）。</p>
     *
     * @param taskId   项目ID
     * @param stepCode 步骤编码
     * @param bo       跳过请求（原因）
     * @return 更新后的步骤状态列表
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_EDIT)
    @Log(title = "跳过流程步骤", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/projects/{taskId}/steps/{stepCode}/skip")
    public R<List<ProjectStepStateVo>> skipStep(@NotNull(message = "项目ID不能为空")
                                                @PathVariable("taskId") Long taskId,
                                                @NotNull(message = "步骤编码不能为空")
                                                @PathVariable("stepCode") String stepCode,
                                                @RequestBody(required = false) SkipStepBo bo) {
        return R.ok(stepStateService.skip(taskId, stepCode, bo == null ? null : bo.getReason()));
    }

    /**
     * 取消跳过（V0.2 R36）：这一步回到"按当前阶段投影"的状态。
     *
     * @param taskId   项目ID
     * @param stepCode 步骤编码
     * @return 更新后的步骤状态列表
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_EDIT)
    @Log(title = "取消跳过流程步骤", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/projects/{taskId}/steps/{stepCode}/skip/cancel")
    public R<List<ProjectStepStateVo>> cancelSkipStep(@NotNull(message = "项目ID不能为空")
                                                      @PathVariable("taskId") Long taskId,
                                                      @NotNull(message = "步骤编码不能为空")
                                                      @PathVariable("stepCode") String stepCode) {
        return R.ok(stepStateService.cancelSkip(taskId, stepCode));
    }

    /**
     * 跳过请求体（只有原因一个字段：其他都从路径与配置来，不由请求决定）。
     */
    @Data
    public static class SkipStepBo {
        /** 跳过原因（必填，2~200 字） */
        private String reason;
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
     * 确认模块计划（V0.2 R28，文档 §25「页面模块已确认」的第 1 步）。
     *
     * <p>确认不是阶段变更，只把计划行的状态置为 CONFIRMED 并留一条事件；
     * 再次保存计划会自动回到"待确认"（改了就得重新确认）。</p>
     *
     * @param taskId 项目ID
     * @return 确认后的规划视图
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_EDIT)
    @Log(title = "模块计划确认", businessType = BusinessType.UPDATE)
    @PostMapping("/projects/{taskId}/module-plan/confirm")
    public R<ProjectModulePlanVo> confirmModulePlan(@PathVariable("taskId") Long taskId) {
        return R.ok(moduleService.confirmPlan(taskId));
    }

    /**
     * 新建模块定义（V0.2 R27，文档 §24 左栏可编辑）。
     *
     * <p>权限用 {@code creative:project:edit}：模块库是"配置"，但它是创作链路的输入，
     * 与"改项目"同一档能力；不新造权限点，避免漏配角色导致 403。</p>
     *
     * @param bo 模块定义
     * @return 保存后的定义
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_EDIT)
    @Log(title = "模块库", businessType = BusinessType.INSERT)
    @PostMapping("/modules")
    public R<DpModuleDefinition> createModule(@RequestBody ModuleDefinitionBo bo) {
        return R.ok(moduleService.createDefinition(bo));
    }

    /**
     * 编辑模块定义（R27）。
     *
     * <p>只影响以后生成的分镜：已生成的分镜屏上冻着当时的屏类型/保真/取景，不回溯改动。</p>
     *
     * @param id 定义ID
     * @param bo 模块定义
     * @return 保存后的定义
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_EDIT)
    @Log(title = "模块库", businessType = BusinessType.UPDATE)
    @PutMapping("/modules/{id}")
    public R<DpModuleDefinition> updateModule(@PathVariable("id") Long id,
                                              @RequestBody ModuleDefinitionBo bo) {
        return R.ok(moduleService.updateDefinition(id, bo));
    }

    /**
     * 启用/停用模块定义（R27）。
     *
     * @param id      定义ID
     * @param enabled '0' 启用 / '1' 停用
     * @return 保存后的定义
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_EDIT)
    @Log(title = "模块库", businessType = BusinessType.UPDATE)
    @PutMapping("/modules/{id}/enabled")
    public R<DpModuleDefinition> setModuleEnabled(@PathVariable("id") Long id,
                                                  @RequestParam("enabled") String enabled) {
        return R.ok(moduleService.setDefinitionEnabled(id, enabled));
    }

    /**
     * 删除模块定义（R27，软删；有项目计划在用则拒绝）。
     *
     * @param id 定义ID
     * @return 结果说明
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_EDIT)
    @Log(title = "模块库", businessType = BusinessType.DELETE)
    @DeleteMapping("/modules/{id}")
    public R<String> deleteModule(@PathVariable("id") Long id) {
        return R.ok(moduleService.deleteDefinition(id));
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

    /**
     * 模块规划视图（V0.2 R22，文档 §24）：左栏模块库 + 中栏当前计划 + 右栏字段 + 屏预览 + 分镜对照。
     *
     * <p>只读：不初始化计划、不写库。项目还没有计划时 {@code modules} 为空，
     * 但 {@code screens} 给的是"按交付类型默认骨架初始化后会长成什么样"，用户点保存才落库。</p>
     *
     * @param taskId 项目ID
     * @return 模块规划视图
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_LIST)
    @GetMapping("/projects/{taskId}/module-plan")
    public R<ProjectModulePlanVo> modulePlan(@PathVariable("taskId") Long taskId) {
        return R.ok(moduleService.planOf(taskId));
    }

    /**
     * 保存模块计划（V0.2 R22，文档 §24）——模块计划的**唯一写入点**。
     *
     * <p>权限用 {@code creative:project:edit}：这是改项目内容，不是"看看"。
     * 写前会 fail-closed 检查不可逆状态（分镜已锁定 / 已出图 / 已渲染），命中直接报错并说明原因。</p>
     *
     * @param taskId 项目ID
     * @param bo     模块列表（顺序即出屏顺序）
     * @return 保存后的规划视图
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_EDIT)
    @Log(title = "模块规划", businessType = BusinessType.UPDATE)
    @PutMapping("/projects/{taskId}/module-plan")
    public R<ProjectModulePlanVo> saveModulePlan(@PathVariable("taskId") Long taskId,
                                                 @RequestBody ProjectModulePlanBo bo) {
        return R.ok(moduleService.savePlan(taskId, bo));
    }
}
