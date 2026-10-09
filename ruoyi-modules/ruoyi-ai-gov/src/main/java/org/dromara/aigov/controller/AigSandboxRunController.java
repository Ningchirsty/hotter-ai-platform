package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.agent.domain.bo.AigSandboxRunRecordBo;
import org.dromara.aigov.agent.evaluation.AigSandboxRunEvidence;
import org.dromara.aigov.agent.service.IAigSandboxRunService;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.common.core.domain.R;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 沙箱运行证据 控制层（发布门槛 {@code SANDBOX_RUN}）。
 *
 * <p><b>它不启动沙箱</b>：作业由宿主侧 worker 执行（ADR-015），本接口只做两件事——
 * 把执行器的 {@code result.json} 原文<b>登记</b>为证据，以及<b>查询</b>某个版本当前的证据结论。
 * 平台后端没有 docker socket，也不该有。</p>
 *
 * <p><b>权限分两档</b>：登记（{@code aig:sandbox:record}）直接决定版本能否从 VALIDATED
 * 走到 SANDBOX_TESTED，只授管理角色；查看（{@code aig:sandbox:list}）解释"为什么不满足"。
 * 登记比查看严，口径同 {@code aig:evaluation:manual}。</p>
 *
 * <p><b>操作人一律取自登录态</b>：请求体里的任何 id 都不作数——账本要回答"是谁把这个结果
 * 记进来的"。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/sandbox")
public class AigSandboxRunController {

    private final IAigSandboxRunService sandboxRunService;

    /**
     * 登记一次沙箱运行（提交执行器输出的 result.json 原文）。
     *
     * <p>{@code @RepeatSubmit}：登记是追加型账本，重复提交只会撞上 jobId 唯一约束；
     * 这里再拦一道，让重复点击得到一个可读提示而不是"已登记过"的报错。</p>
     *
     * @param bo 登记请求
     * @return 新记录ID
     */
    @RepeatSubmit
    @SaCheckPermission(AigConstants.PERM_SANDBOX_RECORD)
    @PostMapping("/run/record")
    public R<Long> record(@RequestBody @Validated AigSandboxRunRecordBo bo) {
        return R.ok(sandboxRunService.record(bo, LoginHelper.getUserId()));
    }

    /**
     * 取某个版本当前的沙箱运行证据结论（页面据此渲染"为什么还不满足"）。
     *
     * @param targetType      对象类型（AGENT_VERSION/SKILL_VERSION/PACKAGE_VERSION）
     * @param targetVersionId 对象版本ID
     * @return 证据结论
     */
    @SaCheckPermission(AigConstants.PERM_SANDBOX_LIST)
    @GetMapping("/run/evidence")
    public R<AigSandboxRunEvidence> evidence(@RequestParam String targetType,
                                            @RequestParam Long targetVersionId) {
        return R.ok(sandboxRunService.sandboxRunEvidence(targetType, targetVersionId));
    }

}
