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
import org.dromara.hrtalent.domain.bo.recruitment.ApplicationTransferBo;
import org.dromara.hrtalent.domain.bo.recruitment.ApplicationTransitionBo;
import org.dromara.hrtalent.domain.bo.recruitment.ArrivalRegisterBo;
import org.dromara.hrtalent.domain.bo.recruitment.NoArrivalBo;
import org.dromara.hrtalent.domain.bo.recruitment.OfferRegisterBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitApplicationBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitApplicationQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitApplicationVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitStageLogVo;
import org.dromara.hrtalent.service.recruitment.IRecruitApplicationService;
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
 * 应聘记录与阶段流转 控制层（SPEC-P3 §2.2）。
 *
 * <p>路径固定为 {@code /recruit/applications}，与前端菜单契约一致；本层只做参数接收与组装 {@link R}，
 * 权限串一律取 {@link HrTalentConstants} 常量。阶段机、六条跳转校验、乐观锁、
 * 报到事务与事件发布全部由 {@link IRecruitApplicationService} 负责。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/recruit/applications")
public class RecruitApplicationController {

    /**
     * 应聘记录服务。
     */
    private final IRecruitApplicationService recruitApplicationService;

    /**
     * 分页查询应聘记录。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 应聘记录分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_LIST)
    @GetMapping
    public R<PageResult<RecruitApplicationVo>> list(RecruitApplicationQueryBo bo, PageQuery pageQuery) {
        return R.ok(recruitApplicationService.queryPage(bo, pageQuery));
    }

    /**
     * 获取应聘记录详情。
     *
     * @param id 应聘记录ID
     * @return 应聘记录详情
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_QUERY)
    @GetMapping("/{id}")
    public R<RecruitApplicationVo> getInfo(@NotNull(message = "应聘记录ID不能为空")
                                           @PathVariable("id") Long id) {
        return R.ok(recruitApplicationService.getDetail(id));
    }

    /**
     * 为已有主档创建应聘记录。
     *
     * @param bo 应聘记录入参
     * @return 新增的应聘记录ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_ADD)
    @Log(title = "应聘记录", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody RecruitApplicationBo bo) {
        return R.ok(recruitApplicationService.create(bo));
    }

    /**
     * 更新应聘记录（仅业务快照字段，带 version 乐观锁）。
     *
     * @param id 应聘记录ID
     * @param bo 应聘记录入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_EDIT)
    @Log(title = "应聘记录", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}")
    public R<Void> edit(@NotNull(message = "应聘记录ID不能为空")
                        @PathVariable("id") Long id,
                        @Validated({Default.class, EditGroup.class}) @RequestBody RecruitApplicationBo bo) {
        bo.setApplicationId(id);
        recruitApplicationService.update(bo);
        return R.ok();
    }

    /**
     * 阶段流转（核心）：阶段前进或转入淘汰/放弃/暂缓/人才保留。
     *
     * @param id 应聘记录ID
     * @param bo 流转入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_STAGE)
    @Log(title = "应聘阶段流转", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/transition")
    public R<Void> transition(@NotNull(message = "应聘记录ID不能为空")
                              @PathVariable("id") Long id,
                              @Validated @RequestBody ApplicationTransitionBo bo) {
        recruitApplicationService.transition(id, bo);
        return R.ok();
    }

    /**
     * 转移招聘负责人或调整应聘岗位。
     *
     * @param id 应聘记录ID
     * @param bo 转移入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_TRANSFER)
    @Log(title = "应聘记录转移", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/transfer")
    public R<Void> transfer(@NotNull(message = "应聘记录ID不能为空")
                            @PathVariable("id") Long id,
                            @Validated @RequestBody ApplicationTransferBo bo) {
        recruitApplicationService.transfer(id, bo);
        return R.ok();
    }

    /**
     * 查询阶段历史（只追加，只读）。
     *
     * @param id 应聘记录ID
     * @return 阶段历史列表
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_QUERY)
    @GetMapping("/{id}/stage-logs")
    public R<List<RecruitStageLogVo>> stageLogs(@NotNull(message = "应聘记录ID不能为空")
                                                @PathVariable("id") Long id) {
        return R.ok(recruitApplicationService.listStageLogs(id));
    }

    /**
     * 登记邀约结果与计划报到日期。
     *
     * @param id 应聘记录ID
     * @param bo 邀约登记入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_STAGE)
    @Log(title = "录用邀约登记", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/offer")
    public R<Void> offer(@NotNull(message = "应聘记录ID不能为空")
                         @PathVariable("id") Long id,
                         @Validated @RequestBody OfferRegisterBo bo) {
        recruitApplicationService.registerOffer(id, bo);
        return R.ok();
    }

    /**
     * 登记实际报到（同一事务内计入月度计划任务到岗人数并刷新任务状态）。
     *
     * @param id 应聘记录ID
     * @param bo 报到登记入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_STAGE)
    @Log(title = "候选人报到登记", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/arrival")
    public R<Void> arrival(@NotNull(message = "应聘记录ID不能为空")
                           @PathVariable("id") Long id,
                           @Validated @RequestBody ArrivalRegisterBo bo) {
        recruitApplicationService.registerArrival(id, bo);
        return R.ok();
    }

    /**
     * 登记未报到及原因。
     *
     * @param id 应聘记录ID
     * @param bo 未报到登记入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_STAGE)
    @Log(title = "候选人未报到登记", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/no-arrival")
    public R<Void> noArrival(@NotNull(message = "应聘记录ID不能为空")
                             @PathVariable("id") Long id,
                             @Validated @RequestBody NoArrivalBo bo) {
        recruitApplicationService.registerNoArrival(id, bo);
        return R.ok();
    }

}
