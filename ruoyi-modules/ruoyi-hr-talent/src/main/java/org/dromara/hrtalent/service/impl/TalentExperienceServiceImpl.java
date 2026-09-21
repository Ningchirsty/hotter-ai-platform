package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.talent.TalentEducationBo;
import org.dromara.hrtalent.domain.bo.talent.TalentEducationQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProjectBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProjectQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentWorkBo;
import org.dromara.hrtalent.domain.bo.talent.TalentWorkQueryBo;
import org.dromara.hrtalent.domain.entity.TalentEducation;
import org.dromara.hrtalent.domain.entity.TalentProject;
import org.dromara.hrtalent.domain.entity.TalentWork;
import org.dromara.hrtalent.domain.vo.talent.TalentEducationVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProjectVo;
import org.dromara.hrtalent.domain.vo.talent.TalentWorkVo;
import org.dromara.hrtalent.mapper.TalentEducationMapper;
import org.dromara.hrtalent.mapper.TalentProjectMapper;
import org.dromara.hrtalent.mapper.TalentWorkMapper;
import org.dromara.hrtalent.service.talent.ITalentExperienceService;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 教育与工作/项目经历服务实现（SPEC-P4 §2.2 B 线、设计文档 §8.14）。
 *
 * <p><b>授权（唯一入口，不得自写规则）</b>：三个域的所有公开方法第一步都调用
 * {@link #requireVisibleTalent(Long)}，其内部委托 {@link ITalentProfileService#requireVisible(Long)}
 * ——该方法即 {@code org.dromara.hrtalent.domainservice.TalentScopeDomainService#checkTalentVisible}
 * 的模块内复用入口。本类<b>不含</b>任何角色、部门、可见范围字段判断（设计文档 §11.1）。</p>
 *
 * <p><b>归属校验</b>：经历主键 {@code education_id}/{@code work_id}/{@code project_id} 是全局自增的，
 * 因此详情/编辑/删除都额外校验 {@code talent_id} 与路径人才一致，删除语句本身也带
 * {@code talent_id} 条件，避免拿别人的经历ID越权读写。</p>
 *
 * <p><b>编辑语义（全量覆盖）</b>：编辑接口把入参视为该条经历的<b>完整目标状态</b>，
 * 业务字段未传即清空；{@code sort_no} 未传按 0；{@code full_time_flag} 未传按 1；
 * {@code current_flag} 未传按 0。</p>
 *
 * <p><b>系统字段不受编辑接口影响</b>：{@code source_type} 与 {@code resume_id} 是来源追溯字段，
 * 编辑时一律沿用库内现值（前端传值被忽略），只由「新增」写入，且解析类来源必须携带
 * 来源简历版本ID。这与设计文档 §8.14「解析生成的内容在人工确认前不得直接覆盖正式经历」一致：
 * 候选结果留在 {@code hr_talent_parse_result}，人工确认后以新增方式落正式经历。</p>
 *
 * <p><b>「是否当前任职」口径</b>：工作经历 {@code current_flag='1'}（在职）时，
 * {@code end_date} 必须为空；服务层在写入前把冲突的 {@code end_date} <b>归一化为 null</b>
 * 并记录一条告警日志（不报错、不留下「在职却有离职日期」的脏数据）。
 * {@code current_flag='0'} 时 {@code end_date} 允许为空，语义是「已离职但日期不详」。
 * 同一口径也写在 {@link TalentWork} 实体类注释中。</p>
 *
 * <p><b>装配方式</b>：BO→实体<b>逐字段显式装配</b>，不使用 {@code MapstructUtils}——
 * 后者的静态成员依赖 Spring 容器中的 {@code Converter} Bean（{@code SpringUtils.getBean}），
 * 在无容器的单元测试中会抛 {@code ExceptionInInitializerError}；显式装配同时让
 * 「哪些字段由服务端权威决定」一目了然。实体→VO 的转换交给
 * {@code BaseMapperPlus#selectVoXxx}（生产环境由 MapStruct 生成的转换器完成）。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentExperienceServiceImpl implements ITalentExperienceService {

    /**
     * 允许的来源类型（设计文档 §8.14：人工录入 / Excel 导入 / 简历解析）。
     */
    private static final Set<String> ALLOWED_SOURCE_TYPES =
        Set.of(SOURCE_MANUAL, SOURCE_IMPORT, SOURCE_RESUME, SOURCE_PARSE);

    /**
     * 必须携带来源简历版本ID的解析类来源。
     */
    private static final Set<String> RESUME_SOURCE_TYPES = Set.of(SOURCE_RESUME, SOURCE_PARSE);

    /**
     * 标志位：是。
     */
    private static final String FLAG_YES = "1";

    /**
     * 标志位：否。
     */
    private static final String FLAG_NO = "0";

    /**
     * 排序号默认值。
     */
    private static final int DEFAULT_SORT_NO = 0;

    /**
     * 教育经历不存在（或不属于该人才）时的统一提示。
     */
    private static final String MSG_EDUCATION_NOT_FOUND = "教育经历不存在或不属于该人才";

    /**
     * 工作经历不存在（或不属于该人才）时的统一提示。
     */
    private static final String MSG_WORK_NOT_FOUND = "工作经历不存在或不属于该人才";

    /**
     * 项目经历不存在（或不属于该人才）时的统一提示。
     */
    private static final String MSG_PROJECT_NOT_FOUND = "项目经历不存在或不属于该人才";

    /**
     * 教育经历 Mapper。
     */
    private final TalentEducationMapper talentEducationMapper;

    /**
     * 工作经历 Mapper。
     */
    private final TalentWorkMapper talentWorkMapper;

    /**
     * 项目经历 Mapper。
     */
    private final TalentProjectMapper talentProjectMapper;

    /**
     * 人才主档服务（可见范围与资源级鉴权的模块内复用入口）。
     */
    private final ITalentProfileService talentProfileService;

    /* ------------------------------------------------------------------ 教育经历 ------------------------------------------------------------------ */

    @Override
    public PageResult<TalentEducationVo> queryEducationPage(Long talentId, TalentEducationQueryBo bo, PageQuery pageQuery) {
        requireVisibleTalent(talentId);
        PageQuery query = pageQuery == null ? new PageQuery() : pageQuery;
        Page<TalentEducationVo> page = talentEducationMapper.selectVoPage(query.build(), educationWrapper(talentId, bo));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public List<TalentEducationVo> listEducation(Long talentId, TalentEducationQueryBo bo) {
        requireVisibleTalent(talentId);
        return talentEducationMapper.selectVoList(educationWrapper(talentId, bo));
    }

    @Override
    public TalentEducationVo getEducationDetail(Long talentId, Long educationId) {
        requireVisibleTalent(talentId);
        return requireOwnedEducation(talentId, educationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createEducation(Long talentId, TalentEducationBo bo) {
        requireVisibleTalent(talentId);
        if (bo == null) {
            throw new ServiceException("教育经历入参不能为空");
        }
        validateDateRange(bo.getStartDate(), bo.getEndDate(), "入学日期", "毕业日期");
        TalentEducation entity = new TalentEducation();
        // 服务端权威字段：主键自增、归属人才取路径、来源类型规范化、排序号兜底
        entity.setTalentId(talentId);
        entity.setSchoolName(bo.getSchoolName());
        entity.setMajor(bo.getMajor());
        entity.setEducation(bo.getEducation());
        entity.setDegree(bo.getDegree());
        entity.setStartDate(bo.getStartDate());
        entity.setEndDate(bo.getEndDate());
        entity.setFullTimeFlag(resolveFlag(bo.getFullTimeFlag(), FLAG_YES, "是否全日制"));
        entity.setSourceType(resolveSourceType(bo.getSourceType(), bo.getResumeId()));
        entity.setResumeId(bo.getResumeId());
        entity.setSortNo(bo.getSortNo() == null ? DEFAULT_SORT_NO : bo.getSortNo());
        entity.setRemark(bo.getRemark());
        talentEducationMapper.insert(entity);
        log.info("新增教育经历, talentId={}, educationId={}, sourceType={}",
            talentId, entity.getEducationId(), entity.getSourceType());
        return entity.getEducationId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEducation(Long talentId, TalentEducationBo bo) {
        if (bo == null || bo.getEducationId() == null) {
            throw new ServiceException("教育经历ID不能为空");
        }
        requireVisibleTalent(talentId);
        requireOwnedEducation(talentId, bo.getEducationId());
        validateDateRange(bo.getStartDate(), bo.getEndDate(), "入学日期", "毕业日期");
        // 全量覆盖：业务字段未传即清空；来源类型/来源简历版本是系统字段，不在编辑范围内
        LambdaUpdateWrapper<TalentEducation> wrapper = new LambdaUpdateWrapper<TalentEducation>()
            .eq(TalentEducation::getEducationId, bo.getEducationId())
            .eq(TalentEducation::getTalentId, talentId)
            .set(TalentEducation::getSchoolName, bo.getSchoolName())
            .set(TalentEducation::getMajor, bo.getMajor())
            .set(TalentEducation::getEducation, bo.getEducation())
            .set(TalentEducation::getDegree, bo.getDegree())
            .set(TalentEducation::getStartDate, bo.getStartDate())
            .set(TalentEducation::getEndDate, bo.getEndDate())
            .set(TalentEducation::getFullTimeFlag, resolveFlag(bo.getFullTimeFlag(), FLAG_YES, "是否全日制"))
            .set(TalentEducation::getSortNo, bo.getSortNo() == null ? DEFAULT_SORT_NO : bo.getSortNo())
            .set(TalentEducation::getRemark, bo.getRemark());
        int rows = talentEducationMapper.update(null, wrapper);
        log.info("更新教育经历, talentId={}, educationId={}, rows={}", talentId, bo.getEducationId(), rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeEducation(Long talentId, Long[] educationIds) {
        requireVisibleTalent(talentId);
        List<Long> ids = distinctIds(educationIds);
        if (ids.isEmpty()) {
            return;
        }
        // 删除条件同时带上 talent_id：混入他人经历ID时也不会被删掉
        LambdaQueryWrapper<TalentEducation> wrapper = new LambdaQueryWrapper<TalentEducation>()
            .in(TalentEducation::getEducationId, ids)
            .eq(TalentEducation::getTalentId, talentId);
        if (talentEducationMapper.selectCount(wrapper) == 0) {
            throw new ServiceException(MSG_EDUCATION_NOT_FOUND);
        }
        // 逻辑删除：@TableLogic 会把 delete 改写为 update del_flag
        talentEducationMapper.delete(wrapper);
        log.info("逻辑删除教育经历, talentId={}, educationIds={}", talentId, ids);
    }

    /* ------------------------------------------------------------------ 工作经历 ------------------------------------------------------------------ */

    @Override
    public PageResult<TalentWorkVo> queryWorkPage(Long talentId, TalentWorkQueryBo bo, PageQuery pageQuery) {
        requireVisibleTalent(talentId);
        PageQuery query = pageQuery == null ? new PageQuery() : pageQuery;
        Page<TalentWorkVo> page = talentWorkMapper.selectVoPage(query.build(), workWrapper(talentId, bo));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public List<TalentWorkVo> listWork(Long talentId, TalentWorkQueryBo bo) {
        requireVisibleTalent(talentId);
        return talentWorkMapper.selectVoList(workWrapper(talentId, bo));
    }

    @Override
    public TalentWorkVo getWorkDetail(Long talentId, Long workId) {
        requireVisibleTalent(talentId);
        return requireOwnedWork(talentId, workId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createWork(Long talentId, TalentWorkBo bo) {
        requireVisibleTalent(talentId);
        if (bo == null) {
            throw new ServiceException("工作经历入参不能为空");
        }
        validateDateRange(bo.getStartDate(), bo.getEndDate(), "入职日期", "离职日期");
        String currentFlag = resolveFlag(bo.getCurrentFlag(), FLAG_NO, "是否当前在职");
        TalentWork entity = new TalentWork();
        // 服务端权威字段：主键自增、归属人才取路径、来源类型规范化、在职口径归一化
        entity.setTalentId(talentId);
        entity.setCompanyName(bo.getCompanyName());
        entity.setDepartmentName(bo.getDepartmentName());
        entity.setPositionName(bo.getPositionName());
        entity.setIndustry(bo.getIndustry());
        entity.setStartDate(bo.getStartDate());
        entity.setEndDate(normalizeWorkEndDate(currentFlag, bo.getEndDate()));
        entity.setLeaveReason(bo.getLeaveReason());
        entity.setCurrentFlag(currentFlag);
        entity.setResponsibility(bo.getResponsibility());
        entity.setAchievement(bo.getAchievement());
        entity.setSourceType(resolveSourceType(bo.getSourceType(), bo.getResumeId()));
        entity.setResumeId(bo.getResumeId());
        entity.setSortNo(bo.getSortNo() == null ? DEFAULT_SORT_NO : bo.getSortNo());
        entity.setRemark(bo.getRemark());
        talentWorkMapper.insert(entity);
        log.info("新增工作经历, talentId={}, workId={}, currentFlag={}, sourceType={}",
            talentId, entity.getWorkId(), currentFlag, entity.getSourceType());
        return entity.getWorkId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWork(Long talentId, TalentWorkBo bo) {
        if (bo == null || bo.getWorkId() == null) {
            throw new ServiceException("工作经历ID不能为空");
        }
        requireVisibleTalent(talentId);
        requireOwnedWork(talentId, bo.getWorkId());
        validateDateRange(bo.getStartDate(), bo.getEndDate(), "入职日期", "离职日期");
        String currentFlag = resolveFlag(bo.getCurrentFlag(), FLAG_NO, "是否当前在职");
        LambdaUpdateWrapper<TalentWork> wrapper = new LambdaUpdateWrapper<TalentWork>()
            .eq(TalentWork::getWorkId, bo.getWorkId())
            .eq(TalentWork::getTalentId, talentId)
            .set(TalentWork::getCompanyName, bo.getCompanyName())
            .set(TalentWork::getDepartmentName, bo.getDepartmentName())
            .set(TalentWork::getPositionName, bo.getPositionName())
            .set(TalentWork::getIndustry, bo.getIndustry())
            .set(TalentWork::getStartDate, bo.getStartDate())
            // 在职口径：与新增同一套归一化，避免编辑后留下「在职却有离职日期」
            .set(TalentWork::getEndDate, normalizeWorkEndDate(currentFlag, bo.getEndDate()))
            .set(TalentWork::getLeaveReason, bo.getLeaveReason())
            .set(TalentWork::getCurrentFlag, currentFlag)
            .set(TalentWork::getResponsibility, bo.getResponsibility())
            .set(TalentWork::getAchievement, bo.getAchievement())
            .set(TalentWork::getSortNo, bo.getSortNo() == null ? DEFAULT_SORT_NO : bo.getSortNo())
            .set(TalentWork::getRemark, bo.getRemark());
        int rows = talentWorkMapper.update(null, wrapper);
        log.info("更新工作经历, talentId={}, workId={}, rows={}", talentId, bo.getWorkId(), rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeWork(Long talentId, Long[] workIds) {
        requireVisibleTalent(talentId);
        List<Long> ids = distinctIds(workIds);
        if (ids.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<TalentWork> wrapper = new LambdaQueryWrapper<TalentWork>()
            .in(TalentWork::getWorkId, ids)
            .eq(TalentWork::getTalentId, talentId);
        if (talentWorkMapper.selectCount(wrapper) == 0) {
            throw new ServiceException(MSG_WORK_NOT_FOUND);
        }
        talentWorkMapper.delete(wrapper);
        log.info("逻辑删除工作经历, talentId={}, workIds={}", talentId, ids);
    }

    /* ------------------------------------------------------------------ 项目经历 ------------------------------------------------------------------ */

    @Override
    public PageResult<TalentProjectVo> queryProjectPage(Long talentId, TalentProjectQueryBo bo, PageQuery pageQuery) {
        requireVisibleTalent(talentId);
        PageQuery query = pageQuery == null ? new PageQuery() : pageQuery;
        Page<TalentProjectVo> page = talentProjectMapper.selectVoPage(query.build(), projectWrapper(talentId, bo));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public List<TalentProjectVo> listProject(Long talentId, TalentProjectQueryBo bo) {
        requireVisibleTalent(talentId);
        return talentProjectMapper.selectVoList(projectWrapper(talentId, bo));
    }

    @Override
    public TalentProjectVo getProjectDetail(Long talentId, Long projectId) {
        requireVisibleTalent(talentId);
        return requireOwnedProject(talentId, projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProject(Long talentId, TalentProjectBo bo) {
        requireVisibleTalent(talentId);
        if (bo == null) {
            throw new ServiceException("项目经历入参不能为空");
        }
        validateDateRange(bo.getStartDate(), bo.getEndDate(), "项目开始日期", "项目结束日期");
        validateProjectWorkRef(talentId, bo.getWorkId());
        TalentProject entity = new TalentProject();
        // 服务端权威字段：主键自增、归属人才取路径、来源类型规范化
        entity.setTalentId(talentId);
        entity.setProjectName(bo.getProjectName());
        entity.setProjectRole(bo.getProjectRole());
        entity.setStartDate(bo.getStartDate());
        entity.setEndDate(bo.getEndDate());
        entity.setDescription(bo.getDescription());
        entity.setResponsibility(bo.getResponsibility());
        entity.setAchievement(bo.getAchievement());
        entity.setSourceType(resolveSourceType(bo.getSourceType(), bo.getResumeId()));
        entity.setResumeId(bo.getResumeId());
        entity.setWorkId(bo.getWorkId());
        entity.setSortNo(bo.getSortNo() == null ? DEFAULT_SORT_NO : bo.getSortNo());
        entity.setRemark(bo.getRemark());
        talentProjectMapper.insert(entity);
        log.info("新增项目经历, talentId={}, projectId={}, sourceType={}",
            talentId, entity.getProjectId(), entity.getSourceType());
        return entity.getProjectId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProject(Long talentId, TalentProjectBo bo) {
        if (bo == null || bo.getProjectId() == null) {
            throw new ServiceException("项目经历ID不能为空");
        }
        requireVisibleTalent(talentId);
        requireOwnedProject(talentId, bo.getProjectId());
        validateDateRange(bo.getStartDate(), bo.getEndDate(), "项目开始日期", "项目结束日期");
        validateProjectWorkRef(talentId, bo.getWorkId());
        LambdaUpdateWrapper<TalentProject> wrapper = new LambdaUpdateWrapper<TalentProject>()
            .eq(TalentProject::getProjectId, bo.getProjectId())
            .eq(TalentProject::getTalentId, talentId)
            .set(TalentProject::getProjectName, bo.getProjectName())
            .set(TalentProject::getProjectRole, bo.getProjectRole())
            .set(TalentProject::getStartDate, bo.getStartDate())
            .set(TalentProject::getEndDate, bo.getEndDate())
            .set(TalentProject::getDescription, bo.getDescription())
            .set(TalentProject::getResponsibility, bo.getResponsibility())
            .set(TalentProject::getAchievement, bo.getAchievement())
            .set(TalentProject::getWorkId, bo.getWorkId())
            .set(TalentProject::getSortNo, bo.getSortNo() == null ? DEFAULT_SORT_NO : bo.getSortNo())
            .set(TalentProject::getRemark, bo.getRemark());
        int rows = talentProjectMapper.update(null, wrapper);
        log.info("更新项目经历, talentId={}, projectId={}, rows={}", talentId, bo.getProjectId(), rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeProject(Long talentId, Long[] projectIds) {
        requireVisibleTalent(talentId);
        List<Long> ids = distinctIds(projectIds);
        if (ids.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<TalentProject> wrapper = new LambdaQueryWrapper<TalentProject>()
            .in(TalentProject::getProjectId, ids)
            .eq(TalentProject::getTalentId, talentId);
        if (talentProjectMapper.selectCount(wrapper) == 0) {
            throw new ServiceException(MSG_PROJECT_NOT_FOUND);
        }
        talentProjectMapper.delete(wrapper);
        log.info("逻辑删除项目经历, talentId={}, projectIds={}", talentId, ids);
    }

    /* ------------------------------------------------------------------ 授权与归属 ------------------------------------------------------------------ */

    /**
     * 访问经历前的人才可见性校验（唯一授权入口）。
     *
     * <p>委托 {@link ITalentProfileService#requireVisible(Long)}，其内部唯一权威为
     * {@code org.dromara.hrtalent.domainservice.TalentScopeDomainService#checkTalentVisible}。
     * 本方法<b>不</b>自行判断角色、部门或可见范围（设计文档 §11.1）。</p>
     *
     * @param talentId 人才主档ID
     */
    private void requireVisibleTalent(Long talentId) {
        if (talentId == null) {
            throw new ServiceException("人才ID不能为空");
        }
        talentProfileService.requireVisible(talentId);
    }

    /**
     * 校验教育经历存在且属于该人才，返回详情 VO。
     *
     * @param talentId    人才主档ID
     * @param educationId 教育经历ID
     * @return 教育经历详情
     */
    private TalentEducationVo requireOwnedEducation(Long talentId, Long educationId) {
        if (educationId == null) {
            throw new ServiceException("教育经历ID不能为空");
        }
        TalentEducationVo vo = talentEducationMapper.selectVoById(educationId);
        if (vo == null || !talentId.equals(vo.getTalentId())) {
            throw new ServiceException(MSG_EDUCATION_NOT_FOUND);
        }
        return vo;
    }

    /**
     * 校验工作经历存在且属于该人才，返回详情 VO。
     *
     * @param talentId 人才主档ID
     * @param workId   工作经历ID
     * @return 工作经历详情
     */
    private TalentWorkVo requireOwnedWork(Long talentId, Long workId) {
        if (workId == null) {
            throw new ServiceException("工作经历ID不能为空");
        }
        TalentWorkVo vo = talentWorkMapper.selectVoById(workId);
        if (vo == null || !talentId.equals(vo.getTalentId())) {
            throw new ServiceException(MSG_WORK_NOT_FOUND);
        }
        return vo;
    }

    /**
     * 校验项目经历存在且属于该人才，返回详情 VO。
     *
     * @param talentId  人才主档ID
     * @param projectId 项目经历ID
     * @return 项目经历详情
     */
    private TalentProjectVo requireOwnedProject(Long talentId, Long projectId) {
        if (projectId == null) {
            throw new ServiceException("项目经历ID不能为空");
        }
        TalentProjectVo vo = talentProjectMapper.selectVoById(projectId);
        if (vo == null || !talentId.equals(vo.getTalentId())) {
            throw new ServiceException(MSG_PROJECT_NOT_FOUND);
        }
        return vo;
    }

    /* ------------------------------------------------------------------ 查询条件 ------------------------------------------------------------------ */

    /**
     * 组装教育经历查询条件（人才归属为硬约束）。
     *
     * @param talentId 人才主档ID
     * @param bo       检索条件，可为空
     * @return 查询条件
     */
    private LambdaQueryWrapper<TalentEducation> educationWrapper(Long talentId, TalentEducationQueryBo bo) {
        TalentEducationQueryBo query = bo == null ? new TalentEducationQueryBo() : bo;
        return new LambdaQueryWrapper<TalentEducation>()
            .eq(TalentEducation::getTalentId, talentId)
            .like(StringUtils.isNotBlank(query.getSchoolName()), TalentEducation::getSchoolName, query.getSchoolName())
            .like(StringUtils.isNotBlank(query.getMajor()), TalentEducation::getMajor, query.getMajor())
            .eq(StringUtils.isNotBlank(query.getEducation()), TalentEducation::getEducation, query.getEducation())
            .eq(StringUtils.isNotBlank(query.getDegree()), TalentEducation::getDegree, query.getDegree())
            .eq(StringUtils.isNotBlank(query.getFullTimeFlag()), TalentEducation::getFullTimeFlag, query.getFullTimeFlag())
            .eq(StringUtils.isNotBlank(query.getSourceType()), TalentEducation::getSourceType, query.getSourceType())
            .ge(query.getStartDateBegin() != null, TalentEducation::getStartDate, query.getStartDateBegin())
            .le(query.getStartDateEnd() != null, TalentEducation::getStartDate, query.getStartDateEnd())
            .orderByDesc(TalentEducation::getStartDate)
            .orderByDesc(TalentEducation::getSortNo)
            .orderByDesc(TalentEducation::getEducationId);
    }

    /**
     * 组装工作经历查询条件（人才归属为硬约束）。
     *
     * @param talentId 人才主档ID
     * @param bo       检索条件，可为空
     * @return 查询条件
     */
    private LambdaQueryWrapper<TalentWork> workWrapper(Long talentId, TalentWorkQueryBo bo) {
        TalentWorkQueryBo query = bo == null ? new TalentWorkQueryBo() : bo;
        return new LambdaQueryWrapper<TalentWork>()
            .eq(TalentWork::getTalentId, talentId)
            .like(StringUtils.isNotBlank(query.getCompanyName()), TalentWork::getCompanyName, query.getCompanyName())
            .like(StringUtils.isNotBlank(query.getDepartmentName()), TalentWork::getDepartmentName, query.getDepartmentName())
            .like(StringUtils.isNotBlank(query.getPositionName()), TalentWork::getPositionName, query.getPositionName())
            .like(StringUtils.isNotBlank(query.getIndustry()), TalentWork::getIndustry, query.getIndustry())
            .eq(StringUtils.isNotBlank(query.getCurrentFlag()), TalentWork::getCurrentFlag, query.getCurrentFlag())
            .eq(StringUtils.isNotBlank(query.getSourceType()), TalentWork::getSourceType, query.getSourceType())
            .ge(query.getStartDateBegin() != null, TalentWork::getStartDate, query.getStartDateBegin())
            .le(query.getStartDateEnd() != null, TalentWork::getStartDate, query.getStartDateEnd())
            .orderByDesc(TalentWork::getStartDate)
            .orderByDesc(TalentWork::getSortNo)
            .orderByDesc(TalentWork::getWorkId);
    }

    /**
     * 组装项目经历查询条件（人才归属为硬约束）。
     *
     * @param talentId 人才主档ID
     * @param bo       检索条件，可为空
     * @return 查询条件
     */
    private LambdaQueryWrapper<TalentProject> projectWrapper(Long talentId, TalentProjectQueryBo bo) {
        TalentProjectQueryBo query = bo == null ? new TalentProjectQueryBo() : bo;
        return new LambdaQueryWrapper<TalentProject>()
            .eq(TalentProject::getTalentId, talentId)
            .like(StringUtils.isNotBlank(query.getProjectName()), TalentProject::getProjectName, query.getProjectName())
            .like(StringUtils.isNotBlank(query.getProjectRole()), TalentProject::getProjectRole, query.getProjectRole())
            .eq(query.getWorkId() != null, TalentProject::getWorkId, query.getWorkId())
            .eq(StringUtils.isNotBlank(query.getSourceType()), TalentProject::getSourceType, query.getSourceType())
            .ge(query.getStartDateBegin() != null, TalentProject::getStartDate, query.getStartDateBegin())
            .le(query.getStartDateEnd() != null, TalentProject::getStartDate, query.getStartDateEnd())
            .orderByDesc(TalentProject::getStartDate)
            .orderByDesc(TalentProject::getSortNo)
            .orderByDesc(TalentProject::getProjectId);
    }

    /* ------------------------------------------------------------------ 规则校验 ------------------------------------------------------------------ */

    /**
     * 校验日期区间：开始日期不得晚于结束日期（任一端为空时放行）。
     *
     * @param start      开始日期，可为空
     * @param end        结束日期，可为空
     * @param startLabel 开始日期中文名
     * @param endLabel   结束日期中文名
     */
    private void validateDateRange(LocalDate start, LocalDate end, String startLabel, String endLabel) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new ServiceException(startLabel + "不能晚于" + endLabel);
        }
    }

    /**
     * 解析 0/1 标志位，空值回落默认值，未知值一律拒绝。
     *
     * @param flag         入参标志，可为空
     * @param defaultValue 默认值
     * @param label        字段中文名
     * @return 规范化后的标志
     */
    private String resolveFlag(String flag, String defaultValue, String label) {
        if (StringUtils.isBlank(flag)) {
            return defaultValue;
        }
        String code = flag.trim();
        if (!FLAG_YES.equals(code) && !FLAG_NO.equals(code)) {
            throw new ServiceException(label + "只能为 0 或 1");
        }
        return code;
    }

    /**
     * 归一化工作经历的离职日期：在职（{@code current_flag='1'}）时强制为空。
     *
     * @param currentFlag 规范化后的在职标志
     * @param endDate     入参离职日期，可为空
     * @return 归一化后的离职日期（在职时为 null）
     */
    private LocalDate normalizeWorkEndDate(String currentFlag, LocalDate endDate) {
        if (FLAG_YES.equals(currentFlag) && endDate != null) {
            // 只记录标志位，不记录任何与个人相关的日期明细
            log.warn("在职工作经历的离职日期被服务端归一化为空, currentFlag={}", currentFlag);
            return null;
        }
        return endDate;
    }

    /**
     * 解析来源类型：只接受稳定编码；解析类来源必须携带来源简历版本ID。
     *
     * @param sourceType 入参来源类型，可为空（回落 manual）
     * @param resumeId   来源简历版本ID，可为空
     * @return 规范化后的来源类型
     */
    private String resolveSourceType(String sourceType, Long resumeId) {
        String code = StringUtils.isBlank(sourceType) ? SOURCE_MANUAL : sourceType.trim();
        if (!ALLOWED_SOURCE_TYPES.contains(code)) {
            throw new ServiceException("来源类型不合法，只允许 manual/import/resume/parse");
        }
        if (RESUME_SOURCE_TYPES.contains(code) && resumeId == null) {
            // §8.14：解析生成的内容必须能追溯到被人工确认的简历版本
            throw new ServiceException("解析来源的经历必须关联来源简历版本ID，且只能在人工确认后写入正式经历");
        }
        return code;
    }

    /**
     * 校验项目关联的工作经历存在且属于同一人才。
     *
     * @param talentId 人才主档ID
     * @param workId   关联工作经历ID，可为空
     */
    private void validateProjectWorkRef(Long talentId, Long workId) {
        if (workId == null) {
            return;
        }
        TalentWork work = talentWorkMapper.selectById(workId);
        if (work == null || !talentId.equals(work.getTalentId())) {
            throw new ServiceException("关联的工作经历不存在或不属于该人才");
        }
    }

    /**
     * 去重、去空后的主键列表。
     *
     * @param ids 主键数组，可为空
     * @return 去重后的列表（不为 null，可能为空）
     */
    private List<Long> distinctIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return List.of();
        }
        return Arrays.stream(ids).filter(Objects::nonNull).distinct().toList();
    }

}
