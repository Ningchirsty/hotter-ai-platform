package org.dromara.talent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.talent.constant.TalentConstants;
import org.dromara.talent.domain.bo.TlParseFieldConfirmBo;
import org.dromara.talent.domain.bo.TlParseTaskCreateBo;
import org.dromara.talent.domain.vo.TlParseFieldVo;
import org.dromara.talent.domain.vo.TlParseTaskVo;
import org.dromara.talent.service.ITalentParseService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 简历解析任务 控制层
 *
 * @author talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent/parse")
public class TalentParseController {

    private final ITalentParseService talentParseService;

    /**
     * 获取解析任务详情（含字段清单，Service 内执行授权校验）。
     *
     * @param taskId 解析任务ID
     * @return 解析任务详情
     */
    @SaCheckPermission(TalentConstants.PERM_PARSE_VIEW)
    @GetMapping("/{taskId}")
    public R<TlParseTaskVo> getInfo(@NotNull(message = "主键不能为空")
                                    @PathVariable("taskId") Long taskId) {
        return R.ok(talentParseService.getTask(taskId));
    }

    /**
     * 查询解析任务的字段复核清单。
     *
     * @param taskId 解析任务ID
     * @return 字段清单（含原始解析值、置信度、确认值）
     */
    @SaCheckPermission(TalentConstants.PERM_PARSE_VIEW)
    @GetMapping("/fields/{taskId}")
    public R<List<TlParseFieldVo>> fields(@NotNull(message = "主键不能为空")
                                          @PathVariable("taskId") Long taskId) {
        return R.ok(talentParseService.listFields(taskId));
    }

    /**
     * 为附件创建解析任务。
     * <p>
     * 解析服务未获批启用时（talent.parse-enabled=false），Service 会把任务落为 DISABLED 状态，
     * 不调用任何外部解析服务，也不会处理真实简历正文。
     * </p>
     *
     * @param bo 创建入参（附件ID）
     * @return 解析任务ID
     */
    @SaCheckPermission(TalentConstants.PERM_PARSE_RETRY)
    @RepeatSubmit
    @PostMapping
    public R<Long> create(@Valid @RequestBody TlParseTaskCreateBo bo) {
        return R.ok(talentParseService.createTask(bo.getAttachmentId()));
    }

    /**
     * 确认解析字段（批量）。
     *
     * @param taskId 解析任务ID
     * @param fields 字段确认清单
     * @return 操作结果
     */
    @SaCheckPermission(TalentConstants.PERM_PARSE_CONFIRM)
    @RepeatSubmit
    @PostMapping("/confirm")
    public R<Void> confirm(@NotNull(message = "主键不能为空") @RequestParam("taskId") Long taskId,
                           @NotEmpty(message = "确认字段不能为空") @Valid @RequestBody List<TlParseFieldConfirmBo> fields) {
        talentParseService.confirmFields(taskId, fields);
        return R.ok();
    }

    /**
     * 重试解析任务。
     *
     * @param taskId 解析任务ID
     * @return 操作结果
     */
    @SaCheckPermission(TalentConstants.PERM_PARSE_RETRY)
    @RepeatSubmit
    @PostMapping("/retry/{taskId}")
    public R<Void> retry(@NotNull(message = "主键不能为空")
                         @PathVariable("taskId") Long taskId) {
        talentParseService.retry(taskId);
        return R.ok();
    }
}
