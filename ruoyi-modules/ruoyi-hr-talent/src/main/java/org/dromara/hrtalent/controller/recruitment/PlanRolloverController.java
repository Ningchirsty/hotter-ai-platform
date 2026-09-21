package org.dromara.hrtalent.controller.recruitment;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.recruitment.RolloverExecuteBo;
import org.dromara.hrtalent.domain.bo.recruitment.RolloverPreviewBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanRolloverVo;
import org.dromara.hrtalent.domain.vo.recruitment.RolloverPreviewVo;
import org.dromara.hrtalent.service.recruitment.IPlanRolloverService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 月度结转中心 控制层（SPEC-P2 §3.3）。
 *
 * <p>路径固定为 {@code /recruit/plan-rollovers}。结转的幂等、单条失败隔离与结转链维护
 * 全部由 {@link IPlanRolloverService} 与其领域服务负责，本层只做参数接收与鉴权。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/recruit/plan-rollovers")
public class PlanRolloverController {

    /**
     * 月度结转服务。
     */
    private final IPlanRolloverService planRolloverService;

    /**
     * 结转记录分页查询（按批次号 / 目标月份 / 结果过滤）。
     *
     * <p>P1 DDL 无独立批次表，批次维度由结转明细的 {@code batchNo} 表达，前端可按其分组。</p>
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 结转明细分页
     */
    @SaCheckPermission(HrTalentConstants.PERM_ROLLOVER_DETAIL)
    @GetMapping
    public R<PageResult<RecruitPlanRolloverVo>> list(RolloverPreviewBo bo, PageQuery pageQuery) {
        return R.ok(planRolloverService.queryPage(bo, pageQuery));
    }

    /**
     * 预览待结转任务、人数与异常项（只读）。
     *
     * @param bo 预览条件
     * @return 预览结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_ROLLOVER_PREVIEW)
    @PostMapping("/preview")
    public R<RolloverPreviewVo> preview(@Validated @RequestBody RolloverPreviewBo bo) {
        return R.ok(planRolloverService.preview(bo));
    }

    /**
     * 执行月度结转（幂等；重复执行不会重复生成下月任务）。
     *
     * @param bo 执行条件
     * @return 执行结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_ROLLOVER_EXECUTE)
    @Log(title = "月度结转执行", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/execute")
    public R<RolloverPreviewVo> execute(@Validated({Default.class}) @RequestBody RolloverExecuteBo bo) {
        return R.ok(planRolloverService.execute(bo));
    }

    /**
     * 重试指定批次中失败的结转条目。
     *
     * @param batchNo 结转批次号
     * @return 重试结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_ROLLOVER_RETRY)
    @Log(title = "月度结转重试", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{batchNo}/retry")
    public R<RolloverPreviewVo> retry(@NotBlank(message = "结转批次号不能为空")
                                      @PathVariable("batchNo") String batchNo) {
        return R.ok(planRolloverService.retry(batchNo));
    }

    /**
     * 查询指定批次的结转明细（含异常明细）。
     *
     * @param batchNo 结转批次号
     * @return 结转明细列表
     */
    @SaCheckPermission(HrTalentConstants.PERM_ROLLOVER_DETAIL)
    @GetMapping("/{batchNo}")
    public R<List<RecruitPlanRolloverVo>> getBatch(@NotBlank(message = "结转批次号不能为空")
                                                   @PathVariable("batchNo") String batchNo) {
        return R.ok(planRolloverService.getBatch(batchNo));
    }

}
