package org.dromara.hrtalent.controller.talent;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.lock.annotation.Lock4j;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.talent.DuplicateConfirmBo;
import org.dromara.hrtalent.domain.bo.talent.DuplicateIgnoreBo;
import org.dromara.hrtalent.domain.bo.talent.TalentDuplicateQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentMergeBo;
import org.dromara.hrtalent.domain.vo.talent.TalentDuplicateCaseVo;
import org.dromara.hrtalent.domain.vo.talent.TalentMergeLogVo;
import org.dromara.hrtalent.domain.vo.talent.TalentMergePreviewVo;
import org.dromara.hrtalent.service.talent.ITalentDuplicateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 重复人才治理与合并 控制层（SPEC-P4 §2.5）。
 *
 * <p>路径固定为 {@code /talent/duplicates}，与前端菜单契约一致；权限串一律取
 * {@link HrTalentConstants} 常量。本层只做参数接收与组装 {@link R}，
 * 可见范围、版本校验、关系转移与合并快照全部由 {@link ITalentDuplicateService} 负责。</p>
 *
 * <p><b>分布式锁</b>：合并接口用 {@code @Lock4j} 按「保留主档ID + 被合并主档ID」加锁
 * （设计文档 §11.1、§21.15 的分布式锁要求），防止并发合并同一对人才；
 * 锁在事务外层获取，方法内的事务提交后才释放。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent/duplicates")
public class TalentDuplicateController {

    /**
     * 重复人才治理服务。
     */
    private final ITalentDuplicateService talentDuplicateService;

    /**
     * 分页查询疑似重复案件（自动套人才可见范围，只返回摘要字段）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 疑似重复案件分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_DUPLICATE_LIST)
    @GetMapping
    public R<PageResult<TalentDuplicateCaseVo>> list(TalentDuplicateQueryBo bo, PageQuery pageQuery) {
        return R.ok(talentDuplicateService.queryPage(bo, pageQuery));
    }

    /**
     * 合并预览：冲突字段差异与关系数量（不落库、不改数据）。
     *
     * @param id 疑似重复案件ID
     * @return 合并预览结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_DUPLICATE_LIST)
    @GetMapping("/{id}/preview")
    public R<TalentMergePreviewVo> preview(@NotNull(message = "疑似重复记录ID不能为空")
                                           @PathVariable("id") Long id) {
        return R.ok(talentDuplicateService.preview(id));
    }

    /**
     * 确认疑似重复案件（是否为同一人）。
     *
     * @param id 疑似重复案件ID
     * @param bo 确认入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_DUPLICATE_CONFIRM)
    @Log(title = "疑似重复人才确认", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@NotNull(message = "疑似重复记录ID不能为空")
                           @PathVariable("id") Long id,
                           @Validated @RequestBody DuplicateConfirmBo bo) {
        talentDuplicateService.confirm(id, bo);
        return R.ok();
    }

    /**
     * 忽略疑似重复案件（非同一人或暂不处理）。
     *
     * @param id 疑似重复案件ID
     * @param bo 忽略入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_DUPLICATE_IGNORE)
    @Log(title = "疑似重复人才忽略", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/ignore")
    public R<Void> ignore(@NotNull(message = "疑似重复记录ID不能为空")
                          @PathVariable("id") Long id,
                          @Validated @RequestBody DuplicateIgnoreBo bo) {
        talentDuplicateService.ignore(id, bo);
        return R.ok();
    }

    /**
     * 事务合并两名人才（本模块最高风险写操作，仅集团人才管理员可执行）。
     *
     * @param id 疑似重复案件ID
     * @param bo 合并入参（含两侧ID、乐观锁版本、冲突字段决策与合并原因）
     * @return 合并日志ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_DUPLICATE_MERGE)
    @Log(title = "人才合并", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @Lock4j(keys = {"#bo.keepTalentId + ':' + #bo.mergedTalentId"}, acquireTimeout = 5000)
    @PostMapping("/{id}/merge")
    public R<Long> merge(@NotNull(message = "疑似重复记录ID不能为空")
                         @PathVariable("id") Long id,
                         @Validated @RequestBody TalentMergeBo bo) {
        return R.ok(talentDuplicateService.merge(id, bo));
    }

    /**
     * 查询合并日志快照（合并后不可普通撤销，只能查看快照）。
     *
     * @param keepTalentId   保留主档ID，可为空
     * @param mergedTalentId 被合并主档ID，可为空
     * @param pageQuery      分页参数
     * @return 合并日志分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_DUPLICATE_MERGE)
    @GetMapping("/merge-logs")
    public R<PageResult<TalentMergeLogVo>> mergeLogs(@RequestParam(value = "keepTalentId", required = false) Long keepTalentId,
                                                     @RequestParam(value = "mergedTalentId", required = false) Long mergedTalentId,
                                                     PageQuery pageQuery) {
        return R.ok(talentDuplicateService.queryMergeLogs(keepTalentId, mergedTalentId, pageQuery));
    }

}
