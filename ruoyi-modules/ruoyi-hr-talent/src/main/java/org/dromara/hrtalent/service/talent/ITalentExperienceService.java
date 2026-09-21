package org.dromara.hrtalent.service.talent;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.talent.TalentEducationBo;
import org.dromara.hrtalent.domain.bo.talent.TalentEducationQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProjectBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProjectQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentWorkBo;
import org.dromara.hrtalent.domain.bo.talent.TalentWorkQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentEducationVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProjectVo;
import org.dromara.hrtalent.domain.vo.talent.TalentWorkVo;

import java.util.List;

/**
 * 教育与工作/项目经历服务接口（SPEC-P4 §2.2 B 线、设计文档 §8.14）。
 *
 * <p><b>授权硬约束</b>：三类经历都挂在人才主档下，<b>任何</b>查询、详情、新增、编辑、删除
 * 调用前都必须先完成人才可见范围校验。实现方统一委托
 * {@link ITalentProfileService#requireVisible(Long)}（其内部唯一权威为
 * {@code org.dromara.hrtalent.domainservice.TalentScopeDomainService#checkTalentVisible}），
 * <b>不得在本域自写任何授权规则</b>（设计文档 §11.1 / §21.14）。</p>
 *
 * <p><b>解析边界</b>（设计文档 §8.14）：简历解析产物保存在
 * {@code hr_talent_parse_result} 候选中，<b>人工确认前不得直接覆盖正式经历</b>。
 * 本接口只负责正式经历的 CRUD；A 线的解析确认流程在人工确认后，通过
 * {@link #createEducation} / {@link #createWork} / {@link #createProject} 并携带
 * {@link #SOURCE_RESUME}（或 {@link #SOURCE_PARSE}）与来源简历版本ID写入正式表。</p>
 *
 * @author hr-talent
 */
public interface ITalentExperienceService {

    /**
     * 来源类型：人工录入（HTTP 写接口的默认来源）。
     */
    String SOURCE_MANUAL = "manual";

    /**
     * 来源类型：Excel 导入（阶段 4 的导入流程使用）。
     */
    String SOURCE_IMPORT = "import";

    /**
     * 来源类型：简历解析结果经人工确认后落正式表。
     */
    String SOURCE_RESUME = "resume";

    /**
     * 来源类型：解析任务候选结果经人工确认后落正式表。
     */
    String SOURCE_PARSE = "parse";

    /* ------------------------------------------------------------------ 教育经历 ------------------------------------------------------------------ */

    /**
     * 分页查询教育经历。
     *
     * @param talentId  人才主档ID
     * @param bo        检索条件，可为空
     * @param pageQuery 分页参数，可为空
     * @return 教育经历分页结果
     */
    PageResult<TalentEducationVo> queryEducationPage(Long talentId, TalentEducationQueryBo bo, PageQuery pageQuery);

    /**
     * 查询教育经历列表（不分页，按入学日期倒序）。
     *
     * @param talentId 人才主档ID
     * @param bo       检索条件，可为空
     * @return 教育经历列表
     */
    List<TalentEducationVo> listEducation(Long talentId, TalentEducationQueryBo bo);

    /**
     * 查询教育经历详情。
     *
     * @param talentId    人才主档ID
     * @param educationId 教育经历ID
     * @return 教育经历详情
     */
    TalentEducationVo getEducationDetail(Long talentId, Long educationId);

    /**
     * 新增教育经历。
     *
     * @param talentId 人才主档ID（以路径为准，覆盖入参）
     * @param bo       教育经历入参
     * @return 新建的教育经历ID
     */
    Long createEducation(Long talentId, TalentEducationBo bo);

    /**
     * 编辑教育经历（仅允许修改属于该人才的经历）。
     *
     * @param talentId 人才主档ID（以路径为准，覆盖入参）
     * @param bo       教育经历入参（必须携带 educationId）
     */
    void updateEducation(Long talentId, TalentEducationBo bo);

    /**
     * 逻辑删除教育经历。
     *
     * @param talentId     人才主档ID
     * @param educationIds 教育经历ID数组
     */
    void removeEducation(Long talentId, Long[] educationIds);

    /* ------------------------------------------------------------------ 工作经历 ------------------------------------------------------------------ */

    /**
     * 分页查询工作经历。
     *
     * @param talentId  人才主档ID
     * @param bo        检索条件，可为空
     * @param pageQuery 分页参数，可为空
     * @return 工作经历分页结果
     */
    PageResult<TalentWorkVo> queryWorkPage(Long talentId, TalentWorkQueryBo bo, PageQuery pageQuery);

    /**
     * 查询工作经历列表（不分页，按入职日期倒序）。
     *
     * @param talentId 人才主档ID
     * @param bo       检索条件，可为空
     * @return 工作经历列表
     */
    List<TalentWorkVo> listWork(Long talentId, TalentWorkQueryBo bo);

    /**
     * 查询工作经历详情。
     *
     * @param talentId 人才主档ID
     * @param workId   工作经历ID
     * @return 工作经历详情
     */
    TalentWorkVo getWorkDetail(Long talentId, Long workId);

    /**
     * 新增工作经历。
     *
     * @param talentId 人才主档ID（以路径为准，覆盖入参）
     * @param bo       工作经历入参
     * @return 新建的工作经历ID
     */
    Long createWork(Long talentId, TalentWorkBo bo);

    /**
     * 编辑工作经历（仅允许修改属于该人才的经历）。
     *
     * @param talentId 人才主档ID（以路径为准，覆盖入参）
     * @param bo       工作经历入参（必须携带 workId）
     */
    void updateWork(Long talentId, TalentWorkBo bo);

    /**
     * 逻辑删除工作经历。
     *
     * @param talentId 人才主档ID
     * @param workIds  工作经历ID数组
     */
    void removeWork(Long talentId, Long[] workIds);

    /* ------------------------------------------------------------------ 项目经历 ------------------------------------------------------------------ */

    /**
     * 分页查询项目经历。
     *
     * @param talentId  人才主档ID
     * @param bo        检索条件，可为空
     * @param pageQuery 分页参数，可为空
     * @return 项目经历分页结果
     */
    PageResult<TalentProjectVo> queryProjectPage(Long talentId, TalentProjectQueryBo bo, PageQuery pageQuery);

    /**
     * 查询项目经历列表（不分页，按开始日期倒序）。
     *
     * @param talentId 人才主档ID
     * @param bo       检索条件，可为空
     * @return 项目经历列表
     */
    List<TalentProjectVo> listProject(Long talentId, TalentProjectQueryBo bo);

    /**
     * 查询项目经历详情。
     *
     * @param talentId  人才主档ID
     * @param projectId 项目经历ID
     * @return 项目经历详情
     */
    TalentProjectVo getProjectDetail(Long talentId, Long projectId);

    /**
     * 新增项目经历。
     *
     * @param talentId 人才主档ID（以路径为准，覆盖入参）
     * @param bo       项目经历入参
     * @return 新建的项目经历ID
     */
    Long createProject(Long talentId, TalentProjectBo bo);

    /**
     * 编辑项目经历（仅允许修改属于该人才的经历）。
     *
     * @param talentId 人才主档ID（以路径为准，覆盖入参）
     * @param bo       项目经历入参（必须携带 projectId）
     */
    void updateProject(Long talentId, TalentProjectBo bo);

    /**
     * 逻辑删除项目经历。
     *
     * @param talentId   人才主档ID
     * @param projectIds 项目经历ID数组
     */
    void removeProject(Long talentId, Long[] projectIds);

}
