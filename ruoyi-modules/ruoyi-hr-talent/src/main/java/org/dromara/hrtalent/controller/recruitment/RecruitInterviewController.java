package org.dromara.hrtalent.controller.recruitment;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.recruitment.InterviewCancelBo;
import org.dromara.hrtalent.domain.bo.recruitment.InterviewFeedbackBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitInterviewBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitInterviewQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitInterviewVo;
import org.dromara.hrtalent.service.recruitment.IRecruitInterviewService;
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
 * 面试管理 控制层（SPEC-P3 §2.3，设计文档 §7.3、§8.6）。
 *
 * <p>路径固定为 {@code /recruit/interviews}，与前端菜单契约一致；权限串一律取
 * {@link HrTalentConstants} 常量。安排/改期/取消/反馈的业务规则与状态流转
 * 全部由 {@link IRecruitInterviewService} 负责，本层只做参数接收与 {@link R} 组装。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/recruit/interviews")
public class RecruitInterviewController {

    /**
     * 面试服务。
     */
    private final IRecruitInterviewService recruitInterviewService;

    /**
     * 分页查询面试记录。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 面试记录分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_INTERVIEW_LIST)
    @GetMapping
    public R<PageResult<RecruitInterviewVo>> list(RecruitInterviewQueryBo bo, PageQuery pageQuery) {
        return R.ok(recruitInterviewService.queryPage(bo, pageQuery));
    }

    /**
     * 面试官待办：当前登录用户作为面试官且尚未反馈的面试，按计划面试时间升序。
     *
     * @return 待办面试列表
     */
    @SaCheckPermission(HrTalentConstants.PERM_INTERVIEW_LIST)
    @GetMapping("/my-todos")
    public R<List<RecruitInterviewVo>> myTodos() {
        return R.ok(recruitInterviewService.myTodos());
    }

    /**
     * 获取面试详情（含各面试官个人意见）。
     *
     * @param id 面试记录ID
     * @return 面试详情
     */
    @SaCheckPermission(HrTalentConstants.PERM_INTERVIEW_LIST)
    @GetMapping("/{id}")
    public R<RecruitInterviewVo> getInfo(@NotNull(message = "面试记录ID不能为空")
                                         @PathVariable("id") Long id) {
        return R.ok(recruitInterviewService.getDetail(id));
    }

    /**
     * 安排面试（一面/二面/扩展轮次，指定时间、方式、地点与一名或多名面试官）。
     *
     * @param bo 面试入参
     * @return 新增的面试记录ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_INTERVIEW_SCHEDULE)
    @Log(title = "面试安排", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody RecruitInterviewBo bo) {
        return R.ok(recruitInterviewService.schedule(bo));
    }

    /**
     * 面试改期（保留操作记录：原记录置为已改期，新增一条有效面试记录）。
     *
     * @param id 面试记录ID
     * @param bo 改期入参
     * @return 改期后新的面试记录ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_INTERVIEW_SCHEDULE)
    @Log(title = "面试改期", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}")
    public R<Long> reschedule(@NotNull(message = "面试记录ID不能为空")
                              @PathVariable("id") Long id,
                              @Validated({Default.class, EditGroup.class}) @RequestBody RecruitInterviewBo bo) {
        bo.setInterviewId(id);
        return R.ok(recruitInterviewService.reschedule(bo));
    }

    /**
     * 取消面试（必须填写原因）。
     *
     * @param id 面试记录ID
     * @param bo 取消入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_INTERVIEW_CANCEL)
    @Log(title = "面试取消", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@NotNull(message = "面试记录ID不能为空")
                          @PathVariable("id") Long id,
                          @Validated @RequestBody InterviewCancelBo bo) {
        bo.setInterviewId(id);
        recruitInterviewService.cancel(bo);
        return R.ok();
    }

    /**
     * 面试官提交个人评分/结论/意见（可选同时提交汇总结论）。
     *
     * @param id 面试记录ID
     * @param bo 反馈入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_INTERVIEW_FEEDBACK)
    @Log(title = "面试反馈", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/feedback")
    public R<Void> feedback(@NotNull(message = "面试记录ID不能为空")
                            @PathVariable("id") Long id,
                            @Validated @RequestBody InterviewFeedbackBo bo) {
        bo.setInterviewId(id);
        recruitInterviewService.feedback(bo);
        return R.ok();
    }

}
