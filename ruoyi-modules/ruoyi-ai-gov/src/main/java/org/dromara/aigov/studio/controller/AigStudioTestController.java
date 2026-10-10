package org.dromara.aigov.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.studio.domain.bo.AigStudioTestRunBo;
import org.dromara.aigov.studio.domain.vo.AigStudioTestRunVo;
import org.dromara.aigov.studio.helper.AigStudioActorProvider;
import org.dromara.aigov.studio.service.IAigStudioTestService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 训练台测试调用接口（专题 C §C3.1 Playground / §C4 沙箱；增量 S5）。
 *
 * <p><b>会花真钱</b>：这是本模块唯一"一次点击就产生一次模型调用"的入口，因此
 * ①默认关闭（配置开关）；②单独权限点 {@code aig:studio:test:run}；
 * ③{@code @RepeatSubmit} 防连点；④调用仍走网关（策略校验/配额/审计都在那一层）。</p>
 *
 * <p>它<b>不是</b> ADR-015/016 那个受限容器沙箱——那条链给"执行不可信代码"用，
 * 本接口只发一次受治理的模型调用，不自建沙箱。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/studio")
public class AigStudioTestController extends BaseController {

    private final IAigStudioTestService testService;
    private final AigStudioActorProvider actorProvider;

    /**
     * 用草稿的当前修订跑一次测试调用。
     *
     * @param draftId 草稿ID
     * @param bo      入参（数据等级必填）
     * @return 测试结果
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_TEST_RUN)
    @RepeatSubmit
    @PostMapping("/drafts/{draftId:\\d+}/test-runs")
    public R<AigStudioTestRunVo> run(@NotNull(message = "草稿ID不能为空") @PathVariable Long draftId,
                                     @RequestBody @Validated AigStudioTestRunBo bo) {
        return R.ok(testService.runTest(draftId, bo, requireActor()));
    }

    /**
     * 读取一条测试证据（不再调用）。
     *
     * @param linkId 证据链ID
     * @return 证据
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_TEST_VIEW)
    @GetMapping("/test-runs/{linkId:\\d+}")
    public R<AigStudioTestRunVo> get(@NotNull(message = "证据链ID不能为空")
                                     @PathVariable Long linkId) {
        return R.ok(testService.getTestRun(linkId));
    }

    /**
     * 取当前操作者；取不到就拒绝（测试会花钱，必须能追到人）。
     *
     * @return 用户ID
     */
    private Long requireActor() {
        Long actorId = actorProvider.currentUserId();
        if (actorId == null) {
            throw new ServiceException("训练台测试需要登录用户：无法确定这次调用的责任人");
        }
        return actorId;
    }

}
