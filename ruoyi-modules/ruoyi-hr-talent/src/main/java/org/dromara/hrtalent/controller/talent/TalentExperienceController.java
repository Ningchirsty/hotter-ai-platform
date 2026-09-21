package org.dromara.hrtalent.controller.talent;

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
import org.dromara.hrtalent.domain.bo.talent.TalentEducationBo;
import org.dromara.hrtalent.domain.bo.talent.TalentEducationQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProjectBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProjectQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentWorkBo;
import org.dromara.hrtalent.domain.bo.talent.TalentWorkQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentEducationVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProjectVo;
import org.dromara.hrtalent.domain.vo.talent.TalentWorkVo;
import org.dromara.hrtalent.service.talent.ITalentExperienceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 教育与工作/项目经历 控制层（SPEC-P4 §2.2 B 线、设计文档 §8.14）。
 *
 * <p><b>路径契约</b>：三类经历都挂在人才主档下，与 SPEC §2.2 逐字一致——
 * {@code /talent/profiles/{id}/educations}、{@code /talent/profiles/{id}/works}、
 * {@code /talent/profiles/{id}/projects}，各自提供分页、列表、详情、新增、编辑、逻辑删除。</p>
 *
 * <p><b>权限</b>：读取（分页/列表/详情）用 {@link HrTalentConstants#PERM_PROFILE_QUERY}，
 * 写入（新增/编辑/删除）用 {@link HrTalentConstants#PERM_PROFILE_EDIT}；
 * <b>不新增权限串</b>，因此无需改菜单 SQL。人才可见范围校验不由本层承担，
 * 一律由服务层经 {@code TalentScopeDomainService} 完成（设计文档 §11.1）。</p>
 *
 * <p><b>人才ID以路径为准</b>：新增/编辑都把路径上的 {@code id} 写入入参，
 * 覆盖请求体中可能伪造的 {@code talentId}，防止把经历写进他人档案。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent/profiles")
public class TalentExperienceController {

    /**
     * 教育与工作/项目经历服务。
     */
    private final ITalentExperienceService talentExperienceService;

    /* ------------------------------------------------------------------ 教育经历 ------------------------------------------------------------------ */

    /**
     * 分页查询教育经历。
     *
     * @param id        人才主档ID
     * @param bo        检索条件
     * @param pageQuery 分页参数
     * @return 教育经历分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_QUERY)
    @GetMapping("/{id}/educations")
    public R<PageResult<TalentEducationVo>> educationPage(@NotNull(message = "人才ID不能为空")
                                                          @PathVariable("id") Long id,
                                                          TalentEducationQueryBo bo,
                                                          PageQuery pageQuery) {
        return R.ok(talentExperienceService.queryEducationPage(id, bo, pageQuery));
    }

    /**
     * 查询教育经历列表（不分页，人才详情页直接渲染）。
     *
     * @param id 人才主档ID
     * @param bo 检索条件
     * @return 教育经历列表
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_QUERY)
    @GetMapping("/{id}/educations/list")
    public R<List<TalentEducationVo>> educationList(@NotNull(message = "人才ID不能为空")
                                                    @PathVariable("id") Long id,
                                                    TalentEducationQueryBo bo) {
        return R.ok(talentExperienceService.listEducation(id, bo));
    }

    /**
     * 获取教育经历详情。
     *
     * @param id          人才主档ID
     * @param educationId 教育经历ID
     * @return 教育经历详情
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_QUERY)
    @GetMapping("/{id}/educations/{educationId}")
    public R<TalentEducationVo> educationInfo(@NotNull(message = "人才ID不能为空")
                                              @PathVariable("id") Long id,
                                              @NotNull(message = "教育经历ID不能为空")
                                              @PathVariable("educationId") Long educationId) {
        return R.ok(talentExperienceService.getEducationDetail(id, educationId));
    }

    /**
     * 新增教育经历（来源缺省为人工录入）。
     *
     * @param id 人才主档ID
     * @param bo 教育经历入参
     * @return 新建的教育经历ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "教育经历", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/{id}/educations")
    public R<Long> educationAdd(@NotNull(message = "人才ID不能为空")
                                @PathVariable("id") Long id,
                                @Validated({Default.class, AddGroup.class}) @RequestBody TalentEducationBo bo) {
        return R.ok(talentExperienceService.createEducation(id, bo));
    }

    /**
     * 编辑教育经历。
     *
     * @param id          人才主档ID
     * @param educationId 教育经历ID
     * @param bo          教育经历入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "教育经历", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}/educations/{educationId}")
    public R<Void> educationEdit(@NotNull(message = "人才ID不能为空")
                                 @PathVariable("id") Long id,
                                 @NotNull(message = "教育经历ID不能为空")
                                 @PathVariable("educationId") Long educationId,
                                 @Validated({Default.class, EditGroup.class}) @RequestBody TalentEducationBo bo) {
        bo.setEducationId(educationId);
        talentExperienceService.updateEducation(id, bo);
        return R.ok();
    }

    /**
     * 逻辑删除教育经历。
     *
     * @param id  人才主档ID
     * @param ids 教育经历ID集合
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "教育经历", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{id}/educations/{ids}")
    public R<Void> educationRemove(@NotNull(message = "人才ID不能为空")
                                   @PathVariable("id") Long id,
                                   @NotNull(message = "教育经历ID不能为空")
                                   @PathVariable("ids") Long[] ids) {
        talentExperienceService.removeEducation(id, ids);
        return R.ok();
    }

    /* ------------------------------------------------------------------ 工作经历 ------------------------------------------------------------------ */

    /**
     * 分页查询工作经历。
     *
     * @param id        人才主档ID
     * @param bo        检索条件
     * @param pageQuery 分页参数
     * @return 工作经历分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_QUERY)
    @GetMapping("/{id}/works")
    public R<PageResult<TalentWorkVo>> workPage(@NotNull(message = "人才ID不能为空")
                                                @PathVariable("id") Long id,
                                                TalentWorkQueryBo bo,
                                                PageQuery pageQuery) {
        return R.ok(talentExperienceService.queryWorkPage(id, bo, pageQuery));
    }

    /**
     * 查询工作经历列表（不分页）。
     *
     * @param id 人才主档ID
     * @param bo 检索条件
     * @return 工作经历列表
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_QUERY)
    @GetMapping("/{id}/works/list")
    public R<List<TalentWorkVo>> workList(@NotNull(message = "人才ID不能为空")
                                          @PathVariable("id") Long id,
                                          TalentWorkQueryBo bo) {
        return R.ok(talentExperienceService.listWork(id, bo));
    }

    /**
     * 获取工作经历详情。
     *
     * @param id     人才主档ID
     * @param workId 工作经历ID
     * @return 工作经历详情
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_QUERY)
    @GetMapping("/{id}/works/{workId}")
    public R<TalentWorkVo> workInfo(@NotNull(message = "人才ID不能为空")
                                    @PathVariable("id") Long id,
                                    @NotNull(message = "工作经历ID不能为空")
                                    @PathVariable("workId") Long workId) {
        return R.ok(talentExperienceService.getWorkDetail(id, workId));
    }

    /**
     * 新增工作经历（「是否当前任职」为真时服务端把离职日期置空）。
     *
     * @param id 人才主档ID
     * @param bo 工作经历入参
     * @return 新建的工作经历ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "工作经历", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/{id}/works")
    public R<Long> workAdd(@NotNull(message = "人才ID不能为空")
                           @PathVariable("id") Long id,
                           @Validated({Default.class, AddGroup.class}) @RequestBody TalentWorkBo bo) {
        return R.ok(talentExperienceService.createWork(id, bo));
    }

    /**
     * 编辑工作经历。
     *
     * @param id     人才主档ID
     * @param workId 工作经历ID
     * @param bo     工作经历入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "工作经历", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}/works/{workId}")
    public R<Void> workEdit(@NotNull(message = "人才ID不能为空")
                            @PathVariable("id") Long id,
                            @NotNull(message = "工作经历ID不能为空")
                            @PathVariable("workId") Long workId,
                            @Validated({Default.class, EditGroup.class}) @RequestBody TalentWorkBo bo) {
        bo.setWorkId(workId);
        talentExperienceService.updateWork(id, bo);
        return R.ok();
    }

    /**
     * 逻辑删除工作经历。
     *
     * @param id  人才主档ID
     * @param ids 工作经历ID集合
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "工作经历", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{id}/works/{ids}")
    public R<Void> workRemove(@NotNull(message = "人才ID不能为空")
                              @PathVariable("id") Long id,
                              @NotNull(message = "工作经历ID不能为空")
                              @PathVariable("ids") Long[] ids) {
        talentExperienceService.removeWork(id, ids);
        return R.ok();
    }

    /* ------------------------------------------------------------------ 项目经历 ------------------------------------------------------------------ */

    /**
     * 分页查询项目经历。
     *
     * @param id        人才主档ID
     * @param bo        检索条件
     * @param pageQuery 分页参数
     * @return 项目经历分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_QUERY)
    @GetMapping("/{id}/projects")
    public R<PageResult<TalentProjectVo>> projectPage(@NotNull(message = "人才ID不能为空")
                                                      @PathVariable("id") Long id,
                                                      TalentProjectQueryBo bo,
                                                      PageQuery pageQuery) {
        return R.ok(talentExperienceService.queryProjectPage(id, bo, pageQuery));
    }

    /**
     * 查询项目经历列表（不分页）。
     *
     * @param id 人才主档ID
     * @param bo 检索条件
     * @return 项目经历列表
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_QUERY)
    @GetMapping("/{id}/projects/list")
    public R<List<TalentProjectVo>> projectList(@NotNull(message = "人才ID不能为空")
                                                @PathVariable("id") Long id,
                                                TalentProjectQueryBo bo) {
        return R.ok(talentExperienceService.listProject(id, bo));
    }

    /**
     * 获取项目经历详情。
     *
     * @param id        人才主档ID
     * @param projectId 项目经历ID
     * @return 项目经历详情
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_QUERY)
    @GetMapping("/{id}/projects/{projectId}")
    public R<TalentProjectVo> projectInfo(@NotNull(message = "人才ID不能为空")
                                          @PathVariable("id") Long id,
                                          @NotNull(message = "项目经历ID不能为空")
                                          @PathVariable("projectId") Long projectId) {
        return R.ok(talentExperienceService.getProjectDetail(id, projectId));
    }

    /**
     * 新增项目经历。
     *
     * @param id 人才主档ID
     * @param bo 项目经历入参
     * @return 新建的项目经历ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "项目经历", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/{id}/projects")
    public R<Long> projectAdd(@NotNull(message = "人才ID不能为空")
                              @PathVariable("id") Long id,
                              @Validated({Default.class, AddGroup.class}) @RequestBody TalentProjectBo bo) {
        return R.ok(talentExperienceService.createProject(id, bo));
    }

    /**
     * 编辑项目经历。
     *
     * @param id        人才主档ID
     * @param projectId 项目经历ID
     * @param bo        项目经历入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "项目经历", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}/projects/{projectId}")
    public R<Void> projectEdit(@NotNull(message = "人才ID不能为空")
                               @PathVariable("id") Long id,
                               @NotNull(message = "项目经历ID不能为空")
                               @PathVariable("projectId") Long projectId,
                               @Validated({Default.class, EditGroup.class}) @RequestBody TalentProjectBo bo) {
        bo.setProjectId(projectId);
        talentExperienceService.updateProject(id, bo);
        return R.ok();
    }

    /**
     * 逻辑删除项目经历。
     *
     * @param id  人才主档ID
     * @param ids 项目经历ID集合
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "项目经历", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{id}/projects/{ids}")
    public R<Void> projectRemove(@NotNull(message = "人才ID不能为空")
                                 @PathVariable("id") Long id,
                                 @NotNull(message = "项目经历ID不能为空")
                                 @PathVariable("ids") Long[] ids) {
        talentExperienceService.removeProject(id, ids);
        return R.ok();
    }

}
