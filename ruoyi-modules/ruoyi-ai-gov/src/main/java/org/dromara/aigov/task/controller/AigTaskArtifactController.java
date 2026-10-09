package org.dromara.aigov.task.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.task.domain.bo.AigTaskArtifactBo;
import org.dromara.aigov.task.domain.vo.AigTaskArtifactVo;
import org.dromara.aigov.task.service.IAigTaskArtifactService;
import org.dromara.common.core.domain.R;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务制品账本（{@code /aigov/task/artifact}）。
 *
 * <p><b>权限为什么沿用任务的读/操作两点</b>：制品是任务产出的延伸，能改任务状态的人
 * （{@code aig:task:operate}）就是能登记制品的人；能看任务的人（{@code aig:task:query}）
 * 就该能看它产出了什么。为它新开权限点会把「能不能登记制品」变成第三件要单独授权的事，
 * 而真正的边界（谁能改任务）没有变化。</p>
 *
 * <p><b>登记失败返回的是错误而不是 200 + 空对象</b>：{@code ServiceException} 会让响应体带
 * 可读原因（含 {@code ARTIFACT_INVALID} 与字段级明细），生产方据此能一次改对；
 * 静默成功会让对方以为制品已交付。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/task/artifact")
public class AigTaskArtifactController extends BaseController {

    private final IAigTaskArtifactService artifactService;

    /**
     * 登记一份任务制品。
     *
     * @param bo 登记入参（任务、类型、MIME、大小、sha256、对象引用）
     * @return 入库的制品
     */
    @SaCheckPermission(AigConstants.PERM_TASK_OPERATE)
    @RepeatSubmit
    @PostMapping
    public R<AigTaskArtifactVo> register(@Validated @RequestBody AigTaskArtifactBo bo) {
        return R.ok(artifactService.register(bo));
    }

    /**
     * 查某任务的制品清单。
     *
     * @param taskId          任务ID
     * @param includeRejected 是否包含被拒记录（默认不含；排查时带上才能看到「对方交过什么」）
     * @return 制品清单（新→旧）
     */
    @SaCheckPermission(AigConstants.PERM_TASK_QUERY)
    @GetMapping("/list")
    public R<List<AigTaskArtifactVo>> list(@NotNull(message = "任务ID不能为空") @RequestParam Long taskId,
                                           @RequestParam(defaultValue = "false") boolean includeRejected) {
        return R.ok(artifactService.listByTask(taskId, includeRejected));
    }

}
