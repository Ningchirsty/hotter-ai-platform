package org.dromara.hrtalent.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.config.HrTalentProperties;
import org.dromara.hrtalent.domain.bo.talent.ParseConfirmBo;
import org.dromara.hrtalent.domain.bo.talent.TalentEducationBo;
import org.dromara.hrtalent.domain.bo.talent.TalentEducationQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProjectBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProjectQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentWorkBo;
import org.dromara.hrtalent.domain.bo.talent.TalentWorkQueryBo;
import org.dromara.hrtalent.domain.entity.TalentParseResult;
import org.dromara.hrtalent.domain.entity.TalentParseTask;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.entity.TalentResume;
import org.dromara.hrtalent.domain.vo.talent.TalentEducationVo;
import org.dromara.hrtalent.domain.vo.talent.TalentParseResultVo;
import org.dromara.hrtalent.domain.vo.talent.TalentParseTaskVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProjectVo;
import org.dromara.hrtalent.domain.vo.talent.TalentWorkVo;
import org.dromara.hrtalent.enums.ResumeParseStatusEnum;
import org.dromara.hrtalent.mapper.TalentParseResultMapper;
import org.dromara.hrtalent.mapper.TalentParseTaskMapper;
import org.dromara.hrtalent.mapper.TalentResumeMapper;
import org.dromara.hrtalent.service.talent.ITalentExperienceService;
import org.dromara.hrtalent.service.talent.ITalentParseService;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.dromara.hrtalent.service.talent.ITalentResumeService;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 简历解析任务与人工复核服务实现（SPEC-P4 §2.1，设计文档 §8.21、§8.14、§11.1、§21.6）。
 *
 * <p><b>一期严格边界</b>：</p>
 * <ul>
 *     <li>创建任务<b>不执行任何解析</b>：不读文件、不做文本提取、不调用 OCR 与大模型，
 *     请求内只写一条 {@code hr_talent_parse_task}（§11.1）；</li>
 *     <li>开关 {@code hrtalent.resume-parse-enabled} 默认 {@code false}（未获个人信息处理批准前必须保持关闭）：
 *     此时任务直接置 {@code failed}，写入稳定错误码 {@link HrTalentErrorCode#HR_RESUME_002} 与明确中文提示；
 *     开关为 {@code true} 时任务停在 {@code pending} 等待异步执行器（一期尚未接入）消费；</li>
 *     <li><b>不引入任何外部解析服务</b>，不新增依赖，杜绝把简历或个人数据发送给未经批准的第三方（§8.21）；</li>
 *     <li>解析结果逐字段保存「原始值 / 标准化值 / 置信度 / 来源位置 / 复核结论」；
 *     低置信度字段默认不勾选（阈值见 {@link #DEFAULT_CONFIDENCE_THRESHOLD}）；
 *     <b>正式字段只有在人工确认后才更新</b>人才主档（§8.21）。</li>
 * </ul>
 *
 * <p><b>人工确认后的落地口径</b>：人才主档字段（姓名、联系方式、城市、公司、岗位、学历、年限、行业、期望薪资等）
 * 由本服务在人工确认后写入人才主档；教育/工作/项目经历字段在人工确认后<b>调用 B 线经历域</b>
 * {@link ITalentExperienceService} 写入正式经历表（{@code hr_talent_education} /
 * {@code hr_talent_work} / {@code hr_talent_project}），本服务<b>不</b>自建同表 Mapper、
 * <b>不</b>直写对方表。写入时统一标 {@link ITalentExperienceService#SOURCE_PARSE} 并携带来源简历版本ID，
 * 保证经历可回溯到 {@code hr_talent_parse_result} 的复核结论（§8.14 解析产物在人工确认前不得覆盖正式经历）。</p>
 *
 * <p><b>经历写入的约束</b>：① 只有 {@code accepted = true} 的字段才写；② 同一份简历同一次确认中，
 * 按字段路径的类别与下标（如 {@code education[0].school_name} 的 {@code education[0]}）聚合成一条经历；
 * ③ 必须有「主字段」才能建记录（教育=学校名称、工作=公司名称、项目=项目名称），
 * 缺主字段时只记复核结论并写入中文提示，不生成半空记录；④ 写入前按「同简历 + 同主字段 + 同开始日期」查重，
 * 避免重复确认造成重复经历，同时保留「同一公司先后两段任职」等真实的多段经历（B 线未提供幂等/批量新增入口，
 * 此处用其列表查询做等价去重）。</p>
 *
 * <p><b>置信度阈值</b>：{@link #DEFAULT_CONFIDENCE_THRESHOLD} 为默认阈值（0.85，即 85%）；
 * 置信度缺失或低于阈值时 {@code defaultSelected = false}。如需按环境调整，
 * 建议后续在 {@code HrTalentProperties} 增加 {@code resume-parse-confidence-threshold} 配置项
 * （本域为避免改动并行开发中的共享配置文件，先以常量形式固定并在此写明）。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentParseServiceImpl implements ITalentParseService {

    /**
     * 默认置信度阈值：低于该值（或置信度缺失）的字段默认<b>不勾选</b>。
     */
    public static final BigDecimal DEFAULT_CONFIDENCE_THRESHOLD = new BigDecimal("0.85");

    /**
     * 任务状态：待解析（解析引擎接入后由异步执行器消费）。
     */
    public static final String TASK_PENDING = "pending";

    /**
     * 任务状态：解析失败。
     */
    public static final String TASK_FAILED = "failed";

    /**
     * 解析器类型：内置（一期不接入任何外部解析服务）。
     */
    public static final String PARSER_TYPE_INTERNAL = "internal";

    /**
     * 复核结论：待复核。
     */
    public static final String REVIEW_PENDING = "pending";

    /**
     * 复核结论：已确认。
     */
    public static final String REVIEW_CONFIRMED = "confirmed";

    /**
     * 复核结论：已否决。
     */
    public static final String REVIEW_REJECTED = "rejected";

    /**
     * 解析引擎未启用时的中文提示（不泄露任何简历内容）。
     */
    public static final String MSG_ENGINE_DISABLED =
        HrTalentErrorCode.MSG_HR_RESUME_002 + "：解析引擎未启用（未获个人信息处理批准前不接入任何外部解析服务），任务已标记失败，可继续人工录入";

    /**
     * 解析引擎启用但执行器未接入时的中文提示。
     */
    public static final String MSG_ENGINE_PENDING =
        "解析任务已创建，等待异步解析引擎执行（当前未接入解析引擎，不发送任何简历数据到外部服务）";

    /**
     * 字段路径既不是人才主档字段、也不是可识别的经历路径时的备注提示。
     */
    private static final String REMARK_UNSUPPORTED_FIELD =
        "该字段路径无法识别，未写入正式数据，请在对应模块人工录入";

    /**
     * 经历字段不在白名单内时的备注提示。
     */
    private static final String REMARK_UNKNOWN_EXPERIENCE_FIELD =
        "该经历字段不在可落库白名单内，未写入正式经历，请人工核对后在经历模块录入";

    /**
     * 支持写入人才主档的字段路径别名 → {@link TalentProfileBo} 属性名。
     *
     * <p>解析时只取字段路径的<b>最后一段</b>（如 {@code basic.name} → {@code name}），
     * 因此经历类路径（{@code education[0].school_name}）不会误命中主档字段。</p>
     */
    private static final Map<String, String> PROFILE_FIELD_ALIASES = buildProfileFieldAliases();

    /**
     * 支持的日期格式（依次尝试）。
     */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy/M/d"),
        DateTimeFormatter.ofPattern("yyyy.M.d"));

    /**
     * 简历解析任务 Mapper。
     */
    private final TalentParseTaskMapper talentParseTaskMapper;

    /**
     * 简历候选解析结果 Mapper。
     */
    private final TalentParseResultMapper talentParseResultMapper;

    /**
     * 简历版本 Mapper（只读简历定位信息）。
     */
    private final TalentResumeMapper talentResumeMapper;

    /**
     * 简历服务（资源级鉴权入口，收敛到 TalentScopeDomainService）。
     */
    private final ITalentResumeService talentResumeService;

    /**
     * 人才主档服务（正式字段只有在人工确认后经此写入）。
     */
    private final ITalentProfileService talentProfileService;

    /**
     * 经历域服务（B 线唯一入口）：教育/工作/项目经历在人工确认后经此写入正式表，
     * 本服务<b>不</b>自建同表 Mapper、不直写对方表（设计文档 §8.14）。
     */
    private final ITalentExperienceService talentExperienceService;

    /**
     * 招聘与人才管理业务配置（解析开关与解析器版本）。
     */
    private final HrTalentProperties hrTalentProperties;

    /* ------------------------------------------------------------------ 创建任务 ------------------------------------------------------------------ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(Long resumeId) {
        // 1) 资源级鉴权：简历不可见时一律拒绝创建任务
        TalentResume resume = talentResumeService.requireVisibleResume(resumeId);
        // 2) 幂等：同一简历已有未结束任务时直接复用，避免重复排队
        TalentParseTask active = talentParseTaskMapper.selectOne(new LambdaQueryWrapper<TalentParseTask>()
            .eq(TalentParseTask::getResumeId, resumeId)
            .in(TalentParseTask::getTaskStatus, TASK_PENDING, "running")
            .orderByDesc(TalentParseTask::getTaskId)
            .last("limit 1"));
        if (active != null) {
            log.info("复用进行中的简历解析任务, resumeId={}, taskId={}", resumeId, active.getTaskId());
            return active.getTaskId();
        }

        boolean enabled = hrTalentProperties.isResumeParseEnabled();
        Long taskId = IdUtil.getSnowflakeNextId();
        TalentParseTask task = new TalentParseTask();
        task.setTaskId(taskId);
        task.setResumeId(resumeId);
        task.setTalentId(resume.getTalentId());
        task.setParserType(PARSER_TYPE_INTERNAL);
        task.setParserVersion(hrTalentProperties.getResumeParserVersion());
        task.setRetryCount(0);
        if (enabled) {
            // 引擎接入后由异步执行器消费；请求内不执行 OCR / 大模型调用（§11.1）
            task.setTaskStatus(TASK_PENDING);
            task.setRemark(MSG_ENGINE_PENDING);
            int rows = talentParseTaskMapper.insert(task);
            if (rows == 0) {
                throw new ServiceException("解析任务创建失败，请重试");
            }
            updateResumeStatus(resumeId, ResumeParseStatusEnum.PENDING, ResumeParseStatusEnum.PENDING, null);
            log.info("创建简历解析任务（待异步执行）, taskId={}, resumeId={}", taskId, resumeId);
            return taskId;
        }
        // 解析引擎未启用：任务直接置失败并给出明确中文提示，绝不把简历数据外发（§8.21）
        task.setTaskStatus(TASK_FAILED);
        task.setStartedTime(LocalDateTime.now());
        task.setFinishedTime(LocalDateTime.now());
        task.setErrorCode(HrTalentErrorCode.HR_RESUME_002);
        task.setErrorMessage(MSG_ENGINE_DISABLED);
        task.setRemark(MSG_ENGINE_DISABLED);
        int rows = talentParseTaskMapper.insert(task);
        if (rows == 0) {
            throw new ServiceException("解析任务创建失败，请重试");
        }
        updateResumeStatus(resumeId, ResumeParseStatusEnum.FAILED, ResumeParseStatusEnum.PENDING, null);
        log.info("简历解析引擎未启用，任务已标记失败, taskId={}, resumeId={}, errorCode={}",
            taskId, resumeId, HrTalentErrorCode.HR_RESUME_002);
        return taskId;
    }

    /* ------------------------------------------------------------------ 任务与候选结果 ------------------------------------------------------------------ */

    @Override
    public TalentParseTaskVo getTask(Long taskId) {
        TalentParseTask task = requireVisibleTask(taskId);
        return buildTaskVo(task);
    }

    /* ------------------------------------------------------------------ 人工确认 ------------------------------------------------------------------ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TalentParseTaskVo confirm(Long taskId, ParseConfirmBo bo) {
        if (bo == null || bo.getItems() == null || bo.getItems().isEmpty()) {
            throw new ServiceException("请至少提交一个待确认的解析字段");
        }
        TalentParseTask task = requireVisibleTask(taskId);
        TalentResume resume = talentResumeMapper.selectById(task.getResumeId());
        if (resume == null) {
            throw new ServiceException("简历不存在或已删除");
        }
        // 候选结果必须属于本任务，避免用其它任务的 resultId 越权写入
        List<TalentParseResult> results = talentParseResultMapper.selectList(new LambdaQueryWrapper<TalentParseResult>()
            .eq(TalentParseResult::getTaskId, taskId));
        Map<Long, TalentParseResult> indexed = new LinkedHashMap<>();
        for (TalentParseResult result : results) {
            indexed.put(result.getResultId(), result);
        }
        Long operatorId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        TalentProfileBo profileBo = new TalentProfileBo();
        // 经历字段按「类别[下标]」聚合，一次确认内同一组只生成一条正式经历
        Map<String, ExperienceGroup> groups = new LinkedHashMap<>();
        List<TalentParseResult> touched = new ArrayList<>();
        int acceptedCount = 0;
        int profileFieldCount = 0;
        for (ParseConfirmBo.Item item : bo.getItems()) {
            if (item == null || item.getResultId() == null) {
                throw new ServiceException("解析结果ID不能为空");
            }
            TalentParseResult result = indexed.get(item.getResultId());
            if (result == null) {
                throw new ServiceException("解析结果不属于该任务，请刷新后重试");
            }
            // 人工修正值优先，未修正时使用解析结果的标准化值
            String value = StringUtils.isNotBlank(item.getNormalizedValue())
                ? item.getNormalizedValue().trim()
                : (result.getNormalizedValue() == null ? null : result.getNormalizedValue().trim());
            boolean accepted = Boolean.TRUE.equals(item.getAccepted());
            String remark = null;
            if (accepted) {
                if (StringUtils.isBlank(value)) {
                    throw new ServiceException("字段 " + result.getFieldPath() + " 的确认值不能为空");
                }
                acceptedCount++;
                if (applyProfileField(profileBo, result.getFieldPath(), value)) {
                    profileFieldCount++;
                } else {
                    // 非主档字段：按教育与工作/项目经历路径归类，人工确认后经 B 线经历域写入正式表
                    remark = bindExperienceField(groups, result, value);
                }
            }
            result.setNormalizedValue(value);
            result.setReviewStatus(accepted ? REVIEW_CONFIRMED : REVIEW_REJECTED);
            result.setReviewedBy(operatorId);
            result.setReviewedTime(now);
            if (remark != null) {
                result.setRemark(remark);
            }
            touched.add(result);
        }
        // 正式字段写入：只有勾选且属于主档的字段才会更新人才主档（§8.21）
        int appliedFields = applyToProfile(task.getTalentId(), profileBo, bo.getRemark(), profileFieldCount);
        // 经历写入：只有勾选且通过白名单、且具备主字段的经历才会写入正式经历表（§8.14）
        int createdExperiences = writeExperienceGroups(groups, task.getTalentId(), resume.getResumeId());
        // 落库复核结论（含经历写入结果回写的备注，作为可追溯来源）
        for (TalentParseResult result : touched) {
            int rows = talentParseResultMapper.updateById(result);
            if (rows == 0) {
                throw new ServiceException("解析结果复核失败，请刷新后重试");
            }
        }
        // 刷新简历与任务层面的复核状态
        long pending = talentParseResultMapper.selectCount(new LambdaQueryWrapper<TalentParseResult>()
            .eq(TalentParseResult::getTaskId, taskId)
            .eq(TalentParseResult::getReviewStatus, REVIEW_PENDING));
        if (pending == 0) {
            updateResumeStatus(resume.getResumeId(), ResumeParseStatusEnum.CONFIRMED,
                ResumeParseStatusEnum.CONFIRMED, bo.getRemark());
        } else {
            updateResumeStatus(resume.getResumeId(), ResumeParseStatusEnum.REVIEWING,
                ResumeParseStatusEnum.REVIEWING, bo.getRemark());
        }
        // 任务有结果后进入「解析成功」语义，人工结论由结果行承载
        if (!TASK_FAILED.equals(task.getTaskStatus()) && !"running".equals(task.getTaskStatus())) {
            updateTaskStatus(taskId, "success", bo.getRemark());
        }
        log.info("简历解析结果人工确认完成, taskId={}, accepted={}, appliedFields={}, createdExperiences={}, pending={}",
            taskId, acceptedCount, appliedFields, createdExperiences, pending);
        return buildTaskVo(requireVisibleTask(taskId));
    }

    /* ------------------------------------------------------------------ 内部方法：经历字段落库（B 线经历域） ------------------------------------------------------------------ */

    /**
     * 把一条已勾选的经历字段绑定到「类别[下标]」分组。
     *
     * <p>只做<b>内存装配</b>，不写库；真正的落库在 {@link #writeExperienceGroups} 中统一完成，
     * 这样同一组的多字段只会生成一条正式经历，且能在写库前校验「主字段是否存在」。</p>
     *
     * @param groups 分组容器
     * @param result 候选结果（提供字段路径与结果ID，用于回写备注）
     * @param value  人工确认值
     * @return 需要写入结果行的中文备注；正常绑定返回 null
     */
    private String bindExperienceField(Map<String, ExperienceGroup> groups, TalentParseResult result, String value) {
        ExperienceTarget target = parseExperiencePath(result.getFieldPath());
        if (target == null) {
            // 既不是人才主档字段，也不是可识别的经历路径
            return REMARK_UNSUPPORTED_FIELD;
        }
        ExperienceGroup group = groups.computeIfAbsent(target.groupKey(),
            key -> new ExperienceGroup(target.category()));
        boolean bound = setExperienceField(group, target, result.getFieldPath(), value);
        if (!bound) {
            return REMARK_UNKNOWN_EXPERIENCE_FIELD;
        }
        group.bind(result);
        return null;
    }

    /**
     * 把分组内的经历写入正式经历表（经 B 线 {@link ITalentExperienceService}，不直写对方表）。
     *
     * <p><b>落库规则</b>：① 缺主字段（教育=学校名称、工作=公司名称、项目=项目名称）不建记录；
     * ② 去重键为「同简历 + 主字段同名 + <b>开始日期相同</b>」，命中才跳过，避免重复确认产生重复经历
     * （B 线未提供幂等/批量新增入口，此处以其列表查询做等价去重）。
     * <b>为什么不只用主字段同名</b>：同一家公司先后两段任职（离职后回归）在真实简历中很常见，
     * 公司名相同而入职日期不同，若只比主字段会把这第二段经历<b>静默丢掉</b>，属于解析确认阶段的数据丢失；
     * 加上 {@code start_date} 后，「同一主体不同经历」会正确保留为两条，而真正的重复确认仍会被去重。
     * 当本次确认值缺少开始日期时，日期比较退化为「两边都为空才相等」，并在跳过备注中写明该口径，
     * 方向是<b>宁可漏去重（多写一条）也不丢数据</b>；
     * ③ 统一标 {@link ITalentExperienceService#SOURCE_PARSE} 并携带来源简历版本ID，满足 B 线
     * 「解析类来源新增必须带 resume_id」的校验；④ 一律<b>新增</b>，绝不覆盖已有正式经历（§8.14）。</p>
     *
     * @param groups   经历分组
     * @param talentId 人才主档ID
     * @param resumeId 来源简历版本ID
     * @return 实际新建的经历条数
     */
    private int writeExperienceGroups(Map<String, ExperienceGroup> groups, Long talentId, Long resumeId) {
        if (talentId == null || groups.isEmpty()) {
            return 0;
        }
        int created = 0;
        for (ExperienceGroup group : groups.values()) {
            if (ExperienceCategory.EDUCATION == group.category()) {
                TalentEducationBo bo = group.educationBo();
                if (StringUtils.isBlank(bo.getSchoolName())) {
                    group.mark("缺少学校名称，未写入正式教育经历；请补全学校名称后重新确认或在经历模块人工录入");
                    continue;
                }
                if (educationExists(talentId, resumeId, bo.getSchoolName(), bo.getStartDate())) {
                    group.mark("该简历已存在同一学校且开始日期相同的教育经历，本次跳过重复写入"
                        + startDateFallbackNote(bo.getStartDate()));
                    continue;
                }
                bo.setSourceType(ITalentExperienceService.SOURCE_PARSE);
                bo.setResumeId(resumeId);
                Long id = talentExperienceService.createEducation(talentId, bo);
                group.mark("已写入教育经历（ID=" + id + "，来源 parse，简历版本 " + resumeId + "）");
                created++;
            } else if (ExperienceCategory.WORK == group.category()) {
                TalentWorkBo bo = group.workBo();
                if (StringUtils.isBlank(bo.getCompanyName())) {
                    group.mark("缺少公司名称，未写入正式工作经历；请补全公司名称后重新确认或在经历模块人工录入");
                    continue;
                }
                if (workExists(talentId, resumeId, bo.getCompanyName(), bo.getStartDate())) {
                    group.mark("该简历已存在同一公司且入职日期相同的经历，本次跳过重复写入"
                        + startDateFallbackNote(bo.getStartDate()));
                    continue;
                }
                bo.setSourceType(ITalentExperienceService.SOURCE_PARSE);
                bo.setResumeId(resumeId);
                Long id = talentExperienceService.createWork(talentId, bo);
                group.mark("已写入工作经历（ID=" + id + "，来源 parse，简历版本 " + resumeId + "）");
                created++;
            } else {
                TalentProjectBo bo = group.projectBo();
                if (StringUtils.isBlank(bo.getProjectName())) {
                    group.mark("缺少项目名称，未写入正式项目经历；请补全项目名称后重新确认或在经历模块人工录入");
                    continue;
                }
                if (projectExists(talentId, resumeId, bo.getProjectName(), bo.getStartDate())) {
                    group.mark("该简历已存在同一项目且开始日期相同的经历，本次跳过重复写入"
                        + startDateFallbackNote(bo.getStartDate()));
                    continue;
                }
                bo.setSourceType(ITalentExperienceService.SOURCE_PARSE);
                bo.setResumeId(resumeId);
                Long id = talentExperienceService.createProject(talentId, bo);
                group.mark("已写入项目经历（ID=" + id + "，来源 parse，简历版本 " + resumeId + "）");
                created++;
            }
        }
        return created;
    }

    /**
     * 缺少开始日期时的去重口径补充说明（写入跳过备注，便于人工判断是否需要补录）。
     *
     * @param startDate 本次确认值的开始日期
     * @return 备注后缀；开始日期非空时返回空串
     */
    private String startDateFallbackNote(LocalDate startDate) {
        return startDate == null
            ? "（该经历未提供开始日期，日期比较退化为双方均为空才算相同，宁可多写一条也不丢数据）"
            : "";
    }

    /**
     * 同简历是否已写入「同主字段 + 同开始日期」的教育经历（去重检查，经 B 线列表接口，不查对方表）。
     *
     * <p>去重键含 {@code start_date}：同一学校先后两段就读会被保留为两条；
     * 开始日期为空时日期比较退化为「双方均为空才相等」，方向是宁可漏去重也不丢数据。</p>
     *
     * @param talentId   人才主档ID
     * @param resumeId   简历版本ID
     * @param schoolName 学校名称
     * @param startDate  入学日期，可为空
     * @return 是否已存在
     */
    private boolean educationExists(Long talentId, Long resumeId, String schoolName, LocalDate startDate) {
        TalentEducationQueryBo bo = new TalentEducationQueryBo();
        bo.setSourceType(ITalentExperienceService.SOURCE_PARSE);
        List<TalentEducationVo> list = talentExperienceService.listEducation(talentId, bo);
        if (list == null) {
            return false;
        }
        return list.stream().anyMatch(vo -> Objects.equals(resumeId, vo.getResumeId())
            && Objects.equals(schoolName, vo.getSchoolName())
            && Objects.equals(startDate, vo.getStartDate()));
    }

    /**
     * 同简历是否已写入「同主字段 + 同开始日期」的工作经历（去重检查）。
     *
     * <p>去重键含 {@code start_date}：同一家公司先后两段任职（离职后回归）公司名相同、
     * 入职日期不同，必须保留为两条，不能按公司名去重丢掉第二段。</p>
     *
     * @param talentId    人才主档ID
     * @param resumeId    简历版本ID
     * @param companyName 公司名称
     * @param startDate   入职日期，可为空
     * @return 是否已存在
     */
    private boolean workExists(Long talentId, Long resumeId, String companyName, LocalDate startDate) {
        TalentWorkQueryBo bo = new TalentWorkQueryBo();
        bo.setSourceType(ITalentExperienceService.SOURCE_PARSE);
        List<TalentWorkVo> list = talentExperienceService.listWork(talentId, bo);
        if (list == null) {
            return false;
        }
        return list.stream().anyMatch(vo -> Objects.equals(resumeId, vo.getResumeId())
            && Objects.equals(companyName, vo.getCompanyName())
            && Objects.equals(startDate, vo.getStartDate()));
    }

    /**
     * 同简历是否已写入「同主字段 + 同开始日期」的项目经历（去重检查）。
     *
     * <p>去重键含 {@code start_date}：同一项目名称的多次参与按开始日期区分。</p>
     *
     * @param talentId    人才主档ID
     * @param resumeId    简历版本ID
     * @param projectName 项目名称
     * @param startDate   项目开始日期，可为空
     * @return 是否已存在
     */
    private boolean projectExists(Long talentId, Long resumeId, String projectName, LocalDate startDate) {
        TalentProjectQueryBo bo = new TalentProjectQueryBo();
        bo.setSourceType(ITalentExperienceService.SOURCE_PARSE);
        List<TalentProjectVo> list = talentExperienceService.listProject(talentId, bo);
        if (list == null) {
            return false;
        }
        return list.stream().anyMatch(vo -> Objects.equals(resumeId, vo.getResumeId())
            && Objects.equals(projectName, vo.getProjectName())
            && Objects.equals(startDate, vo.getStartDate()));
    }

    /**
     * 解析经历字段路径（如 {@code education[0].school_name}）。
     *
     * <p>类别取路径首段（education / work / project）；分组键取方括号下标（缺省 0），
     * 使 {@code education[0].school_name} 与 {@code education[0].major} 落在同一组；
     * 字段名取最后一段并归一化（去下标、小写、连字符转下划线）。</p>
     *
     * @param fieldPath 字段路径，可为空
     * @return 解析结果；不属于三类经历时返回 null
     */
    private ExperienceTarget parseExperiencePath(String fieldPath) {
        if (StringUtils.isBlank(fieldPath)) {
            return null;
        }
        String value = fieldPath.trim();
        int bracket = value.indexOf('[');
        int dot = value.indexOf('.');
        int cut = (bracket >= 0 && (dot < 0 || bracket < dot)) ? bracket : dot;
        String head = (cut >= 0 ? value.substring(0, cut) : value).trim().toLowerCase(Locale.ROOT);
        ExperienceCategory category = ExperienceCategory.find(head);
        if (category == null) {
            return null;
        }
        String index = "0";
        if (bracket >= 0) {
            int close = value.indexOf(']', bracket);
            String raw = close > bracket ? value.substring(bracket + 1, close).trim() : "";
            if (!raw.isEmpty()) {
                index = raw;
            }
        }
        int lastDot = value.lastIndexOf('.');
        String tail = lastDot >= 0 ? value.substring(lastDot + 1) : value;
        String fieldName = tail.replaceAll("\\[[^]]*]", "").trim().toLowerCase(Locale.ROOT)
            .replace('-', '_').replace(' ', '_');
        return new ExperienceTarget(category, category.getCode() + "[" + index + "]", fieldName);
    }

    /**
     * 按类别把确认值写入对应的经历入参对象（只做内存装配 + 值类型校验）。
     *
     * @param group     分组
     * @param target    字段定位
     * @param fieldPath 原始字段路径（用于错误提示）
     * @param value     人工确认值
     * @return 是否命中白名单字段（false 表示该经历字段不受支持）
     */
    private boolean setExperienceField(ExperienceGroup group, ExperienceTarget target, String fieldPath, String value) {
        return switch (target.category()) {
            case EDUCATION -> setEducationField(group.educationBo(), target.fieldName(), fieldPath, value);
            case WORK -> setWorkField(group.workBo(), target.fieldName(), fieldPath, value);
            case PROJECT -> setProjectField(group.projectBo(), target.fieldName(), fieldPath, value);
        };
    }

    /**
     * 教育经历字段白名单装配。
     *
     * @param bo        教育经历入参
     * @param fieldName 归一化字段名
     * @param fieldPath 原始字段路径（错误提示用）
     * @param value     确认值
     * @return 是否命中白名单
     */
    private boolean setEducationField(TalentEducationBo bo, String fieldName, String fieldPath, String value) {
        switch (fieldName) {
            case "school_name", "school" -> bo.setSchoolName(value);
            case "major" -> bo.setMajor(value);
            case "education", "education_level" -> bo.setEducation(value);
            case "degree" -> bo.setDegree(value);
            case "start_date", "start" -> bo.setStartDate(parseDate(fieldPath, value));
            case "end_date", "end" -> bo.setEndDate(parseDate(fieldPath, value));
            case "full_time_flag", "full_time" -> bo.setFullTimeFlag(parseFlag(fieldPath, value));
            case "remark", "description" -> bo.setRemark(value);
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * 工作经历字段白名单装配。
     *
     * @param bo        工作经历入参
     * @param fieldName 归一化字段名
     * @param fieldPath 原始字段路径（错误提示用）
     * @param value     确认值
     * @return 是否命中白名单
     */
    private boolean setWorkField(TalentWorkBo bo, String fieldName, String fieldPath, String value) {
        switch (fieldName) {
            case "company_name", "company" -> bo.setCompanyName(value);
            case "department_name", "department" -> bo.setDepartmentName(value);
            case "position_name", "position", "title" -> bo.setPositionName(value);
            case "industry" -> bo.setIndustry(value);
            case "start_date", "start" -> bo.setStartDate(parseDate(fieldPath, value));
            case "end_date", "end" -> bo.setEndDate(parseDate(fieldPath, value));
            case "current_flag", "current" -> bo.setCurrentFlag(parseFlag(fieldPath, value));
            case "responsibility", "duty" -> bo.setResponsibility(value);
            case "achievement" -> bo.setAchievement(value);
            case "leave_reason", "reason" -> bo.setLeaveReason(value);
            case "remark" -> bo.setRemark(value);
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * 项目经历字段白名单装配。
     *
     * @param bo        项目经历入参
     * @param fieldName 归一化字段名
     * @param fieldPath 原始字段路径（错误提示用）
     * @param value     确认值
     * @return 是否命中白名单
     */
    private boolean setProjectField(TalentProjectBo bo, String fieldName, String fieldPath, String value) {
        switch (fieldName) {
            case "project_name", "name" -> bo.setProjectName(value);
            case "project_role", "role" -> bo.setProjectRole(value);
            case "start_date", "start" -> bo.setStartDate(parseDate(fieldPath, value));
            case "end_date", "end" -> bo.setEndDate(parseDate(fieldPath, value));
            case "description" -> bo.setDescription(value);
            case "responsibility", "duty" -> bo.setResponsibility(value);
            case "achievement" -> bo.setAchievement(value);
            case "remark" -> bo.setRemark(value);
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析布尔标志值（0/1、是/否、true/false、y/n）。
     *
     * @param fieldPath 字段路径（错误提示用）
     * @param value     确认值
     * @return {@code '1'}（是）或 {@code '0'}（否）
     */
    private String parseFlag(String fieldPath, String value) {
        String text = value.trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "1", "是", "true", "y", "yes" -> "1";
            case "0", "否", "false", "n", "no" -> "0";
            default -> throw new ServiceException("字段 " + fieldPath + " 的确认值不是合法的是/否标志：" + value);
        };
    }

    /* ------------------------------------------------------------------ 内部方法：落库与状态 ------------------------------------------------------------------ */

    /**
     * 把勾选的字段写入人才主档（带乐观锁版本号，失败提示刷新重试）。
     *
     * @param talentId          人才主档ID
     * @param profileBo         已装配的主档入参
     * @param remark            确认备注
     * @param profileFieldCount 命中主档字段的勾选数量（为 0 时不发起主档更新）
     * @return 实际提交的主档字段数
     */
    private int applyToProfile(Long talentId, TalentProfileBo profileBo, String remark, int profileFieldCount) {
        if (talentId == null || profileFieldCount <= 0) {
            return 0;
        }
        TalentProfile profile = talentProfileService.requireVisible(talentId);
        profileBo.setTalentId(talentId);
        // 乐观锁：必须携带最新版本号，冲突时由主档服务给出中文提示
        profileBo.setVersion(profile.getVersion());
        profileBo.setRemark(remark);
        talentProfileService.update(profileBo);
        return profileFieldCount;
    }

    /**
     * 将单个解析字段写入主档入参对象。
     *
     * @param bo        主档入参
     * @param fieldPath 解析字段路径（如 {@code basic.name}）
     * @param value     人工确认值
     * @return 是否为主档支持的字段（false 表示经历类字段，仅记录复核结论）
     */
    private boolean applyProfileField(TalentProfileBo bo, String fieldPath, String value) {
        String property = PROFILE_FIELD_ALIASES.get(canonicalField(fieldPath));
        if (property == null) {
            return false;
        }
        switch (property) {
            case "name" -> bo.setName(value);
            case "formerName" -> bo.setFormerName(value);
            case "gender" -> bo.setGender(value);
            case "birthDate" -> bo.setBirthDate(parseDate(fieldPath, value));
            case "ageSnapshot" -> bo.setAgeSnapshot(parseInteger(fieldPath, value));
            case "highestEducation" -> bo.setHighestEducation(value);
            case "phone" -> bo.setPhone(value);
            case "backupPhone" -> bo.setBackupPhone(value);
            case "email" -> bo.setEmail(value);
            case "otherContact" -> bo.setOtherContact(value);
            case "currentCity" -> bo.setCurrentCity(value);
            case "expectedCity" -> bo.setExpectedCity(value);
            case "currentCompany" -> bo.setCurrentCompany(value);
            case "currentPosition" -> bo.setCurrentPosition(value);
            case "expectedPosition" -> bo.setExpectedPosition(value);
            case "expectedSalaryMin" -> bo.setExpectedSalaryMin(parseDecimal(fieldPath, value));
            case "expectedSalaryMax" -> bo.setExpectedSalaryMax(parseDecimal(fieldPath, value));
            case "workYears" -> bo.setWorkYears(parseInteger(fieldPath, value));
            case "industry" -> bo.setIndustry(value);
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * 更新简历的解析状态与复核状态。
     *
     * <p><b>两个字典不同</b>：{@code parse_status} 写字典 {@code talent_resume_parse_status}，
     * {@code review_status} 写字典 {@code talent_resume_review_status}；本方法只使用
     * {@code pending / reviewing / confirmed} 这三个在两个字典中语义一致的编码，
     * 因此复用 {@link ResumeParseStatusEnum} 传值不会写出字典外的取值。</p>
     *
     * @param resumeId     简历版本ID
     * @param parseStatus  解析状态（字典 talent_resume_parse_status）
     * @param reviewStatus 复核状态（字典 talent_resume_review_status）
     * @param remark       备注，可为空（为空时不覆盖原备注）
     */
    private void updateResumeStatus(Long resumeId, ResumeParseStatusEnum parseStatus,
                                   ResumeParseStatusEnum reviewStatus, String remark) {
        LambdaUpdateWrapper<TalentResume> wrapper = new LambdaUpdateWrapper<TalentResume>()
            .eq(TalentResume::getResumeId, resumeId)
            .set(TalentResume::getParseStatus, parseStatus.getCode())
            .set(TalentResume::getReviewStatus, reviewStatus.getCode())
            .set(StringUtils.isNotBlank(remark), TalentResume::getRemark, remark);
        talentResumeMapper.update(null, wrapper);
    }

    /**
     * 更新解析任务状态。
     *
     * @param taskId 任务ID
     * @param status 任务状态
     * @param remark 备注，可为空
     */
    private void updateTaskStatus(Long taskId, String status, String remark) {
        LambdaUpdateWrapper<TalentParseTask> wrapper = new LambdaUpdateWrapper<TalentParseTask>()
            .eq(TalentParseTask::getTaskId, taskId)
            .set(TalentParseTask::getTaskStatus, status)
            .set(TalentParseTask::getFinishedTime, LocalDateTime.now())
            .set(StringUtils.isNotBlank(remark), TalentParseTask::getRemark, remark);
        talentParseTaskMapper.update(null, wrapper);
    }

    /* ------------------------------------------------------------------ 内部方法：查询与装配 ------------------------------------------------------------------ */

    /**
     * 读取解析任务并完成人才资源级鉴权（不可见一律拒绝）。
     *
     * @param taskId 任务ID
     * @return 解析任务实体
     */
    private TalentParseTask requireVisibleTask(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("解析任务ID不能为空");
        }
        TalentParseTask task = talentParseTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException("解析任务不存在或已删除");
        }
        // 任务本身不承载授权信息，鉴权统一回到简历 → 人才资源级鉴权
        talentResumeService.requireVisibleResume(task.getResumeId());
        return task;
    }

    /**
     * 装配任务视图（含候选结果与低置信度默认勾选标记）。
     *
     * @param task 任务实体
     * @return 任务视图
     */
    private TalentParseTaskVo buildTaskVo(TalentParseTask task) {
        TalentParseTaskVo vo = talentParseTaskMapper.selectVoById(task.getTaskId());
        if (vo == null) {
            throw new ServiceException("解析任务不存在或已删除");
        }
        vo.setResults(buildResultVos(task.getTaskId()));
        return vo;
    }

    /**
     * 装配候选结果视图列表。
     *
     * @param taskId 任务ID
     * @return 候选结果列表（按来源位置与主键排序）
     */
    private List<TalentParseResultVo> buildResultVos(Long taskId) {
        List<TalentParseResult> results = talentParseResultMapper.selectList(new LambdaQueryWrapper<TalentParseResult>()
            .eq(TalentParseResult::getTaskId, taskId)
            .orderByAsc(TalentParseResult::getSourceLocation)
            .orderByAsc(TalentParseResult::getResultId));
        List<TalentParseResultVo> vos = new ArrayList<>();
        if (results == null) {
            return vos;
        }
        for (TalentParseResult result : results) {
            TalentParseResultVo vo = new TalentParseResultVo();
            vo.setResultId(result.getResultId());
            vo.setTaskId(result.getTaskId());
            vo.setFieldPath(result.getFieldPath());
            vo.setRawValue(result.getRawValue());
            vo.setNormalizedValue(result.getNormalizedValue());
            vo.setConfidence(result.getConfidence());
            vo.setSourceLocation(result.getSourceLocation());
            vo.setReviewStatus(result.getReviewStatus());
            // 复核状态中文标签统一由字典 talent_resume_review_status 经 @Translation 产出，服务层不再维护第二套映射
            vo.setReviewedBy(result.getReviewedBy());
            vo.setReviewedTime(result.getReviewedTime());
            vo.setRemark(result.getRemark());
            // 低置信度（或置信度缺失）默认不勾选，最终由人工确认决定是否写入正式字段
            vo.setDefaultSelected(isConfidentEnough(result.getConfidence()));
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 置信度是否达到默认勾选阈值。
     *
     * @param confidence 置信度，可为空
     * @return 是否默认勾选
     */
    private boolean isConfidentEnough(BigDecimal confidence) {
        return confidence != null && confidence.compareTo(DEFAULT_CONFIDENCE_THRESHOLD) >= 0;
    }

    /* ------------------------------------------------------------------ 内部方法：解析与转换 ------------------------------------------------------------------ */

    /**
     * 构造主档字段别名表。
     *
     * @return 别名 → 属性名映射
     */
    private static Map<String, String> buildProfileFieldAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("name", "name");
        aliases.put("former_name", "formerName");
        aliases.put("formername", "formerName");
        aliases.put("gender", "gender");
        aliases.put("sex", "gender");
        aliases.put("birth_date", "birthDate");
        aliases.put("birthday", "birthDate");
        aliases.put("age", "ageSnapshot");
        aliases.put("age_snapshot", "ageSnapshot");
        aliases.put("highest_education", "highestEducation");
        aliases.put("education_level", "highestEducation");
        aliases.put("degree", "highestEducation");
        aliases.put("phone", "phone");
        aliases.put("mobile", "phone");
        aliases.put("phone_number", "phone");
        aliases.put("backup_phone", "backupPhone");
        aliases.put("email", "email");
        aliases.put("other_contact", "otherContact");
        aliases.put("wechat", "otherContact");
        aliases.put("current_city", "currentCity");
        aliases.put("expected_city", "expectedCity");
        aliases.put("current_company", "currentCompany");
        aliases.put("current_position", "currentPosition");
        aliases.put("expected_position", "expectedPosition");
        aliases.put("expected_salary_min", "expectedSalaryMin");
        aliases.put("expected_salary_max", "expectedSalaryMax");
        aliases.put("work_years", "workYears");
        aliases.put("industry", "industry");
        return Map.copyOf(aliases);
    }

    /**
     * 归一化字段路径：取最后一段、去掉数组下标、统一小写与下划线。
     *
     * @param fieldPath 原始字段路径，可为空
     * @return 归一化键；无法解析时返回空串
     */
    private String canonicalField(String fieldPath) {
        if (StringUtils.isBlank(fieldPath)) {
            return "";
        }
        String value = fieldPath.trim();
        int dot = value.lastIndexOf('.');
        if (dot >= 0) {
            value = value.substring(dot + 1);
        }
        value = value.replaceAll("\\[[^]]*]", "").trim().toLowerCase(Locale.ROOT);
        value = value.replace('-', '_').replace(' ', '_');
        while (value.contains("__")) {
            value = value.replace("__", "_");
        }
        return value;
    }

    /**
     * 解析日期值（支持 {@code yyyy-MM-dd}、{@code yyyy/M/d}、{@code yyyy.M.d}）。
     *
     * @param fieldPath 字段路径（用于错误提示）
     * @param value     待解析值
     * @return 日期
     */
    private LocalDate parseDate(String fieldPath, String value) {
        String text = value.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        // 只给出年月时按当月 1 日处理（简历常见写法）
        if (text.matches("\\d{4}[-/.]\\d{1,2}")) {
            String[] parts = text.split("[-/.]");
            return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 1);
        }
        throw new ServiceException("字段 " + fieldPath + " 的确认值不是合法日期：" + text);
    }

    /**
     * 解析整数值。
     *
     * @param fieldPath 字段路径（用于错误提示）
     * @param value     待解析值
     * @return 整数
     */
    private Integer parseInteger(String fieldPath, String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("字段 " + fieldPath + " 的确认值不是合法整数：" + value);
        }
    }

    /**
     * 解析金额/数值。
     *
     * @param fieldPath 字段路径（用于错误提示）
     * @param value     待解析值
     * @return 数值
     */
    private BigDecimal parseDecimal(String fieldPath, String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("字段 " + fieldPath + " 的确认值不是合法数值：" + value);
        }
    }

    /**
     * 取当前登录用户ID；无登录态时返回 null。
     *
     * @return 用户ID或 null
     */
    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /* ------------------------------------------------------------------ 模型定义 ------------------------------------------------------------------ */

    /**
     * 经历类别（字段路径首段与分组键前缀）。
     *
     * @author hr-talent
     */
    private enum ExperienceCategory {

        /**
         * 教育经历（hr_talent_education）。
         */
        EDUCATION("education"),
        /**
         * 工作经历（hr_talent_work）。
         */
        WORK("work"),
        /**
         * 项目经历（hr_talent_project）。
         */
        PROJECT("project");

        /**
         * 稳定编码（字段路径首段）。
         */
        private final String code;

        ExperienceCategory(String code) {
            this.code = code;
        }

        /**
         * 取稳定编码。
         *
         * @return 编码
         */
        public String getCode() {
            return code;
        }

        /**
         * 按字段路径首段查找类别。
         *
         * @param code 首段编码（小写）
         * @return 类别；未命中返回 null
         */
        public static ExperienceCategory find(String code) {
            if (code == null) {
                return null;
            }
            for (ExperienceCategory item : values()) {
                if (item.code.equals(code)) {
                    return item;
                }
            }
            return null;
        }

    }

    /**
     * 经历字段定位（类别 + 分组键 + 归一化字段名）。
     *
     * @param category  经历类别
     * @param groupKey  分组键（如 {@code education[0]}）
     * @param fieldName 归一化字段名（如 {@code school_name}）
     * @author hr-talent
     */
    private record ExperienceTarget(ExperienceCategory category, String groupKey, String fieldName) {
    }

    /**
     * 一次确认内的经历装配分组：同一「类别[下标]」的多字段合成一条正式经历。
     *
     * @author hr-talent
     */
    private static final class ExperienceGroup {

        /**
         * 经历类别。
         */
        private final ExperienceCategory category;

        /**
         * 教育经历入参（仅教育类别使用）。
         */
        private final TalentEducationBo educationBo = new TalentEducationBo();

        /**
         * 工作经历入参（仅工作类别使用）。
         */
        private final TalentWorkBo workBo = new TalentWorkBo();

        /**
         * 项目经历入参（仅项目类别使用）。
         */
        private final TalentProjectBo projectBo = new TalentProjectBo();

        /**
         * 属于本组的候选结果（用于回写落库结论备注）。
         */
        private final List<TalentParseResult> results = new ArrayList<>();

        ExperienceGroup(ExperienceCategory category) {
            this.category = category;
        }

        /**
         * 取经历类别。
         *
         * @return 类别
         */
        ExperienceCategory category() {
            return category;
        }

        /**
         * 取教育经历入参。
         *
         * @return 教育经历入参
         */
        TalentEducationBo educationBo() {
            return educationBo;
        }

        /**
         * 取工作经历入参。
         *
         * @return 工作经历入参
         */
        TalentWorkBo workBo() {
            return workBo;
        }

        /**
         * 取项目经历入参。
         *
         * @return 项目经历入参
         */
        TalentProjectBo projectBo() {
            return projectBo;
        }

        /**
         * 绑定一条候选结果。
         *
         * @param result 候选结果
         */
        void bind(TalentParseResult result) {
            results.add(result);
        }

        /**
         * 把落库结论写入本组全部候选结果的备注（保持复核结论可追溯）。
         *
         * @param remark 中文结论
         */
        void mark(String remark) {
            for (TalentParseResult result : results) {
                result.setRemark(remark);
            }
        }

    }

}
