package org.dromara.hrtalent.controller.talent;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.talent.ParseConfirmBo;
import org.dromara.hrtalent.domain.vo.talent.TalentParseTaskVo;
import org.dromara.hrtalent.service.talent.ITalentParseService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简历解析任务与人工复核 控制层（SPEC-P4 §2.1）。
 *
 * <p>路径固定为 {@code /talent/parse-tasks}；权限串一律取 {@link HrTalentConstants}。
 * 解析任务创建在 {@code /talent/resumes/{id}/parse}（见
 * {@link TalentResumeController}），本层只负责任务查询与人工确认。</p>
 *
 * <p>一期解析引擎未接入，任务会停在 {@code pending} 或置 {@code failed} 并给出明确中文提示；
 * 无论引擎是否接入，正式字段都<b>只有</b>在人工确认后才写入人才主档（§8.21）。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent/parse-tasks")
public class TalentParseController {

    /**
     * 简历解析任务服务。
     */
    private final ITalentParseService talentParseService;

    /**
     * 查询解析任务与候选结果（含低置信度默认不勾选标记）。
     *
     * @param id 解析任务ID
     * @return 解析任务视图（含候选结果）
     */
    @SaCheckPermission(HrTalentConstants.PERM_RESUME_REVIEW)
    @GetMapping("/{id}")
    public R<TalentParseTaskVo> getInfo(@NotNull(message = "解析任务ID不能为空")
                                        @PathVariable("id") Long id) {
        return R.ok(talentParseService.getTask(id));
    }

    /**
     * 人工确认选定字段并更新人才主档（未勾选字段记为已否决，不写入正式数据）。
     *
     * @param id 解析任务ID
     * @param bo 逐字段确认入参
     * @return 复核后的解析任务视图
     */
    @SaCheckPermission(HrTalentConstants.PERM_RESUME_REVIEW)
    @Log(title = "简历解析复核", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/confirm")
    public R<TalentParseTaskVo> confirm(@NotNull(message = "解析任务ID不能为空")
                                        @PathVariable("id") Long id,
                                        @Validated({Default.class, EditGroup.class}) @RequestBody ParseConfirmBo bo) {
        return R.ok(talentParseService.confirm(id, bo));
    }

}
