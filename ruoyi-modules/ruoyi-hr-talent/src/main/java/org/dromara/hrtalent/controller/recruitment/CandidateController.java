package org.dromara.hrtalent.controller.recruitment;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.talent.CandidateCreateBo;
import org.dromara.hrtalent.domain.bo.talent.CandidateQueryBo;
import org.dromara.hrtalent.domain.bo.talent.PhoneViewBo;
import org.dromara.hrtalent.domain.bo.talent.TalentPrecheckBo;
import org.dromara.hrtalent.domain.vo.talent.CandidateVo;
import org.dromara.hrtalent.domain.vo.talent.TalentPrecheckVo;
import org.dromara.hrtalent.service.talent.ICandidateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 候选人 控制层（SPEC-P3 §2.1「一人一档」）。
 *
 * <p>路径固定为 {@code /recruit/candidates}：候选人<b>不是</b>独立实体，
 * 列表是存在应聘记录的人才视图，因此本层不做候选人主表读写，
 * 一律委托 {@link ICandidateService}。</p>
 *
 * <p><b>电话明文</b>：{@code POST /{id}/phone-view} 必须先写审计再返发明文，
 * 用途为空时直接拒绝（SPEC-P3 §3.6）；具体审计逻辑在服务层，本层不做任何绕过。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/recruit/candidates")
public class CandidateController {

    /**
     * 候选人服务。
     */
    private final ICandidateService candidateService;

    /**
     * 分页查询候选人（存在应聘记录的人才视图，电话/邮箱脱敏）。
     *
     * @param bo        检索条件
     * @param pageQuery 分页参数
     * @return 候选人分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_LIST)
    @GetMapping
    public R<PageResult<CandidateVo>> list(CandidateQueryBo bo, PageQuery pageQuery) {
        return R.ok(candidateService.queryPage(bo, pageQuery));
    }

    /**
     * 新增候选人（查重后创建主档 + 可选应聘记录，或复用已有主档）。
     *
     * @param bo 候选人入参
     * @return 人才主档ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_ADD)
    @Log(title = "候选人", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody CandidateCreateBo bo) {
        return R.ok(candidateService.create(bo));
    }

    /**
     * 候选人重复预检（不落库，返回强/中/弱匹配摘要）。
     *
     * @param bo 预检入参
     * @return 分级预检结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_ADD)
    @PostMapping("/precheck")
    public R<TalentPrecheckVo> precheck(@Validated @RequestBody TalentPrecheckBo bo) {
        return R.ok(candidateService.precheck(bo));
    }

    /**
     * 记录用途后返回电话明文（写敏感操作审计）。
     *
     * @param id 候选人（人才主档）ID
     * @param bo 查看用途入参
     * @return 电话明文
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_PHONE_VIEW)
    @Log(title = "候选人电话查看", businessType = BusinessType.OTHER)
    @RepeatSubmit
    @PostMapping("/{id}/phone-view")
    public R<String> phoneView(@NotNull(message = "候选人ID不能为空")
                               @PathVariable("id") Long id,
                               @Validated @RequestBody PhoneViewBo bo) {
        return R.ok(candidateService.viewPhone(id, bo.getPurpose()));
    }

}
