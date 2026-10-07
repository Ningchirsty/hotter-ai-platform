package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.agent.domain.bo.AigAgentBindingBo;
import org.dromara.aigov.agent.domain.bo.AigAgentBindingQueryBo;
import org.dromara.aigov.agent.domain.bo.AigAgentQueryBo;
import org.dromara.aigov.agent.domain.bo.AigAgentVersionQueryBo;
import org.dromara.aigov.agent.domain.bo.AigPackageQueryBo;
import org.dromara.aigov.agent.domain.bo.AigPackageVersionQueryBo;
import org.dromara.aigov.agent.domain.bo.AigReleaseAdvanceBo;
import org.dromara.aigov.agent.domain.bo.AigSkillQueryBo;
import org.dromara.aigov.agent.domain.bo.AigSkillVersionQueryBo;
import org.dromara.aigov.agent.domain.vo.AigAgentBindingVo;
import org.dromara.aigov.agent.domain.vo.AigAgentVersionVo;
import org.dromara.aigov.agent.domain.vo.AigAgentVo;
import org.dromara.aigov.agent.domain.vo.AigPackageVersionVo;
import org.dromara.aigov.agent.domain.vo.AigPackageVo;
import org.dromara.aigov.agent.domain.vo.AigSkillVersionVo;
import org.dromara.aigov.agent.domain.vo.AigSkillVo;
import org.dromara.aigov.agent.enums.AigReleaseGateEnum;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.dromara.aigov.agent.manifest.AigManifestScanResult;
import org.dromara.aigov.agent.service.IAigAgentRegistryQueryService;
import org.dromara.aigov.agent.service.IAigAgentRegistryService;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent / Skill / Package 注册中心 控制层（设计 §5、§6、§10.2）。
 *
 * <p>本层只做参数接收与组装 {@link R}；发布门槛的判据、证据核对、并发保护一律由 Service 负责。</p>
 *
 * <p><b>三个写接口刻意分成三个权限</b>：</p>
 * <ul>
 *     <li>{@code aig:agent:release} —— 推进发布状态（唯一写入口，能把版本放给业务用）；</li>
 *     <li>{@code aig:package:scan} —— Manifest 扫描（结论是「Manifest 校验」门槛的唯一证据）；</li>
 *     <li>{@code aig:agent:binding} —— 绑定范围（决定受限通道发布后谁能看见）。</li>
 * </ul>
 * <p>它们都改会改变别人能看到什么，因此不与「查看清单」共用一个权限。
 * 三个写接口都加了 {@link RepeatSubmit}：审批页面双击、网络重试都不该产生第二次推进。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/agent")
public class AigAgentRegistryController {

    private final IAigAgentRegistryService registryService;

    private final IAigAgentRegistryQueryService queryService;

    /**
     * 分页查询 Agent 清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_AGENT_LIST)
    @GetMapping("/list")
    public R<PageResult<AigAgentVo>> list(
        @Validated({Default.class, QueryGroup.class}) AigAgentQueryBo bo, PageQuery pageQuery) {
        return R.ok(queryService.queryAgentPage(bo, pageQuery));
    }

    /**
     * 取 Agent 详情。
     *
     * @param agentId Agent ID
     * @return 详情
     */
    @SaCheckPermission(AigConstants.PERM_AGENT_QUERY)
    @GetMapping("/{agentId:\\d+}")
    public R<AigAgentVo> getAgent(@NotNull(message = "Agent ID不能为空") @PathVariable Long agentId) {
        return R.ok(queryService.getAgent(agentId));
    }

    /**
     * 分页查询 Agent 版本清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_AGENT_LIST)
    @GetMapping("/version/list")
    public R<PageResult<AigAgentVersionVo>> versionList(
        @Validated({Default.class, QueryGroup.class}) AigAgentVersionQueryBo bo, PageQuery pageQuery) {
        return R.ok(queryService.queryAgentVersionPage(bo, pageQuery));
    }

    /**
     * 取 Agent 版本详情。
     *
     * @param agentVersionId 版本ID
     * @return 详情
     */
    @SaCheckPermission(AigConstants.PERM_AGENT_QUERY)
    @GetMapping("/version/{agentVersionId:\\d+}")
    public R<AigAgentVersionVo> getVersion(
        @NotNull(message = "Agent 版本ID不能为空") @PathVariable Long agentVersionId) {
        return R.ok(queryService.getAgentVersion(agentVersionId));
    }

    /**
     * 推进发布状态（<b>唯一写入口</b>）。
     *
     * @param bo 推进入参（含「我以为它现在是什么状态」）
     * @return 推进后的状态
     */
    @SaCheckPermission(AigConstants.PERM_AGENT_RELEASE)
    @RepeatSubmit
    @PostMapping("/release/advance")
    public R<AigReleaseStatusEnum> advanceRelease(@RequestBody @Validated AigReleaseAdvanceBo bo) {
        return R.ok(registryService.advanceRelease(bo));
    }

    /**
     * 取当前状态前进所缺的门槛（供页面显示「还差：沙箱运行、人工批准」）。
     *
     * @param targetType      对象类型
     * @param targetVersionId 对象版本ID
     * @param passedGates     本次认为已通过的门槛编码（可空）
     * @return 尚缺的门槛编码
     */
    @SaCheckPermission(AigConstants.PERM_AGENT_LIST)
    @GetMapping("/release/missing-gates")
    public R<List<String>> missingGates(
        @NotBlank(message = "对象类型不能为空") @RequestParam String targetType,
        @NotNull(message = "对象版本ID不能为空") @RequestParam Long targetVersionId,
        @RequestParam(required = false) List<String> passedGates) {
        Set<AigReleaseGateEnum> passed = new LinkedHashSet<>();
        if (passedGates != null) {
            for (String code : passedGates) {
                AigReleaseGateEnum gate = AigReleaseGateEnum.find(code);
                if (gate == null) {
                    throw new org.dromara.common.core.exception.ServiceException(
                        "未知的门槛编码：" + code);
                }
                passed.add(gate);
            }
        }
        Set<AigReleaseGateEnum> missing = registryService.missingGates(targetType, targetVersionId,
            passed);
        List<String> codes = new ArrayList<>();
        for (AigReleaseGateEnum gate : missing) {
            codes.add(gate.getCode());
        }
        return R.ok(codes);
    }

    /**
     * 新增 Agent 版本绑定（限定品牌/部门/测试项目）。
     *
     * @param bo 绑定入参
     * @return 绑定ID
     */
    @SaCheckPermission(AigConstants.PERM_AGENT_BINDING)
    @RepeatSubmit
    @PostMapping("/binding")
    public R<Long> addBinding(@RequestBody @Validated AigAgentBindingBo bo) {
        return R.ok(registryService.addBinding(bo));
    }

    /**
     * 分页查询 Agent 版本绑定清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_AGENT_LIST)
    @GetMapping("/binding/list")
    public R<PageResult<AigAgentBindingVo>> bindingList(
        @Validated({Default.class, QueryGroup.class}) AigAgentBindingQueryBo bo, PageQuery pageQuery) {
        return R.ok(queryService.queryBindingPage(bo, pageQuery));
    }

    /**
     * 分页查询 Package 清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_PACKAGE_LIST)
    @GetMapping("/package/list")
    public R<PageResult<AigPackageVo>> packageList(
        @Validated({Default.class, QueryGroup.class}) AigPackageQueryBo bo, PageQuery pageQuery) {
        return R.ok(queryService.queryPackagePage(bo, pageQuery));
    }

    /**
     * 取 Package 详情。
     *
     * @param packageId Package ID
     * @return 详情
     */
    @SaCheckPermission(AigConstants.PERM_PACKAGE_QUERY)
    @GetMapping("/package/{packageId:\\d+}")
    public R<AigPackageVo> getPackage(
        @NotNull(message = "Package ID不能为空") @PathVariable Long packageId) {
        return R.ok(queryService.getPackage(packageId));
    }

    /**
     * 分页查询 Package 版本清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_PACKAGE_LIST)
    @GetMapping("/package/version/list")
    public R<PageResult<AigPackageVersionVo>> packageVersionList(
        @Validated({Default.class, QueryGroup.class}) AigPackageVersionQueryBo bo,
        PageQuery pageQuery) {
        return R.ok(queryService.queryPackageVersionPage(bo, pageQuery));
    }

    /**
     * 取 Package 版本详情（含扫描结论）。
     *
     * @param packageVersionId 版本ID
     * @return 详情
     */
    @SaCheckPermission(AigConstants.PERM_PACKAGE_QUERY)
    @GetMapping("/package/version/{packageVersionId:\\d+}")
    public R<AigPackageVersionVo> getPackageVersion(
        @NotNull(message = "Package 版本ID不能为空") @PathVariable Long packageVersionId) {
        return R.ok(queryService.getPackageVersion(packageVersionId));
    }

    /**
     * 扫描 Package 版本的 Manifest（§6.2 五类拒绝规则）并落结论。
     *
     * @param packageVersionId 版本ID
     * @return 扫描结论（含命中的规则与说明）
     */
    @SaCheckPermission(AigConstants.PERM_PACKAGE_SCAN)
    @RepeatSubmit
    @PostMapping("/package/version/{packageVersionId:\\d+}/scan")
    public R<AigManifestScanResult> scanManifest(
        @NotNull(message = "Package 版本ID不能为空") @PathVariable Long packageVersionId) {
        return R.ok(registryService.scanStoredManifest(packageVersionId));
    }

    /**
     * 分页查询 Skill 清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_SKILL_LIST)
    @GetMapping("/skill/list")
    public R<PageResult<AigSkillVo>> skillList(
        @Validated({Default.class, QueryGroup.class}) AigSkillQueryBo bo, PageQuery pageQuery) {
        return R.ok(queryService.querySkillPage(bo, pageQuery));
    }

    /**
     * 分页查询 Skill 版本清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_SKILL_LIST)
    @GetMapping("/skill/version/list")
    public R<PageResult<AigSkillVersionVo>> skillVersionList(
        @Validated({Default.class, QueryGroup.class}) AigSkillVersionQueryBo bo, PageQuery pageQuery) {
        return R.ok(queryService.querySkillVersionPage(bo, pageQuery));
    }

}
