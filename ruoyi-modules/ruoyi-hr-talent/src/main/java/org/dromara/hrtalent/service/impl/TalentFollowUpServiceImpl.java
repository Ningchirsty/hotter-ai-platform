package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.domain.bo.talent.TalentFollowUpBo;
import org.dromara.hrtalent.domain.bo.talent.TalentFollowUpQueryBo;
import org.dromara.hrtalent.domain.entity.TalentFollowUp;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.vo.talent.TalentFollowUpVo;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.enums.ContactResultEnum;
import org.dromara.hrtalent.mapper.TalentFollowUpMapper;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.service.talent.ITalentFollowUpService;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 人才跟进 服务实现（SPEC-P4 §2.4、设计文档 §8.16）。
 *
 * <p><b>实现要点</b>：</p>
 * <ul>
 *     <li><b>访问前必鉴权</b>：列表 / 详情 / 新增 / 编辑 / 删除一律先经
 *     {@link ITalentProfileService#requireVisible(Long)}（内部走
 *     {@link TalentScopeDomainService}），不自行实现任何可见范围规则（§11.1）；</li>
 *     <li><b>与招聘阶段历史分开</b>（§8.16）：本服务只写 {@code hr_talent_follow_up}，
 *     <b>绝不</b>写 {@code hr_recruit_stage_log}，也不触发任何应聘阶段流转；</li>
 *     <li><b>沟通摘要校验</b>（§8.16）：落库前先做长度校验（对齐 DDL {@code varchar(1000)}），
 *     再做内容校验——命中身份证号 / 银行卡号 / 健康病史 / 家庭婚姻 / 精确住址 / 宗教信仰等
 *     与招聘无关的高度敏感个人信息时，给出中文提示并拒绝入库；</li>
 *     <li><b>跟进人不伪造</b>：入参未指定跟进人时回落为当前登录用户，不接受前端伪造他人身份；</li>
 *     <li><b>同步主档冗余字段</b>：新增 / 编辑 / 删除后重算
 *     {@code hr_talent_profile.last_follow_time} 与 {@code next_follow_time}
 *     （条件更新、不递增 {@code version}），使 §8.17「按最近联系时间检索」真正可用；
 *     本类是主档这两列的第二个写入方（第一个是主档自身的创建/编辑流程），详见
 *     {@link #syncProfileFollowTime(Long)}。</li>
 * </ul>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentFollowUpServiceImpl implements ITalentFollowUpService {

    /**
     * 沟通摘要最大长度（与 DDL {@code summary varchar(1000)} 一致）。
     */
    private static final int SUMMARY_MAX_LENGTH = 1000;

    /**
     * 中文提示：沟通摘要过长。
     */
    private static final String MSG_SUMMARY_TOO_LONG = "跟进摘要长度不能超过 1000 个字符";

    /**
     * 中文提示：沟通摘要含与招聘无关的高度敏感个人信息。
     */
    public static final String MSG_SUMMARY_SENSITIVE =
        "沟通摘要不得包含与招聘无关的高度敏感个人信息（如身份证号、银行卡号、健康病史、家庭婚姻、精确住址、宗教信仰等），请只记录与招聘相关的内容";

    /**
     * 18 位身份证号（末位可为 X）。
     */
    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");

    /**
     * 银行卡号（连续 16~19 位数字）。
     */
    private static final Pattern BANK_CARD = Pattern.compile("\\d{16,19}");

    /**
     * 与招聘无关的高度敏感个人信息关键词（§8.16）。
     * <p>只做关键词拦截，不试图穷举；命中即拒绝并提示改写，避免把就业歧视性信息沉淀为可检索资料。</p>
     */
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
        "身份证", "银行卡", "病史", "患病", "疾病", "病历", "诊断", "艾滋病", "乙肝", "精神病",
        "残疾", "怀孕", "孕期", "婚育", "婚姻", "已婚", "未婚", "离婚", "子女", "生育", "家庭住址",
        "宗教信仰", "民族", "政治面貌", "征信", "犯罪记录", "案底", "药检", "吸毒"
    );

    /**
     * 跟进记录 Mapper。
     */
    private final TalentFollowUpMapper talentFollowUpMapper;

    /**
     * 人才主档服务（提供可见性校验入口，不重复实现授权规则）。
     */
    private final ITalentProfileService talentProfileService;

    /**
     * 人才主档 Mapper（<b>只写</b> {@code last_follow_time} / {@code next_follow_time} 两个冗余字段）。
     */
    private final TalentProfileMapper talentProfileMapper;

    @Override
    public PageResult<TalentFollowUpVo> queryPage(Long talentId, TalentFollowUpQueryBo bo, PageQuery pageQuery) {
        // 访问前必须校验该人才对当前用户可见
        talentProfileService.requireVisible(talentId);
        TalentFollowUpQueryBo query = bo == null ? new TalentFollowUpQueryBo() : bo;
        LambdaQueryWrapper<TalentFollowUp> wrapper = new LambdaQueryWrapper<TalentFollowUp>()
            .eq(TalentFollowUp::getTalentId, talentId)
            .eq(StringUtils.isNotBlank(query.getContactMethod()), TalentFollowUp::getContactMethod, query.getContactMethod())
            .eq(StringUtils.isNotBlank(query.getContactResult()), TalentFollowUp::getContactResult, query.getContactResult())
            .eq(query.getFollowerId() != null, TalentFollowUp::getFollowerId, query.getFollowerId())
            .ge(query.getContactDateBegin() != null, TalentFollowUp::getContactTime,
                query.getContactDateBegin() == null ? null : query.getContactDateBegin().atStartOfDay())
            .le(query.getContactDateEnd() != null, TalentFollowUp::getContactTime,
                query.getContactDateEnd() == null ? null : query.getContactDateEnd().atTime(23, 59, 59))
            .ge(query.getNextContactDateBegin() != null, TalentFollowUp::getNextContactTime,
                query.getNextContactDateBegin() == null ? null : query.getNextContactDateBegin().atStartOfDay())
            .le(query.getNextContactDateEnd() != null, TalentFollowUp::getNextContactTime,
                query.getNextContactDateEnd() == null ? null : query.getNextContactDateEnd().atTime(23, 59, 59))
            .orderByDesc(TalentFollowUp::getContactTime)
            .orderByDesc(TalentFollowUp::getFollowId);
        Page<TalentFollowUpVo> page = talentFollowUpMapper.selectVoPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public TalentFollowUpVo getDetail(Long followId) {
        TalentFollowUp entity = requireFollowUp(followId);
        talentProfileService.requireVisible(entity.getTalentId());
        TalentFollowUpVo vo = MapstructUtils.convert(entity, TalentFollowUpVo.class);
        if (vo == null) {
            throw new ServiceException("跟进记录不存在");
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long talentId, TalentFollowUpBo bo) {
        if (bo == null) {
            throw new ServiceException("跟进入参不能为空");
        }
        // 访问前必须校验该人才对当前用户可见
        talentProfileService.requireVisible(talentId);
        validateSummary(bo.getSummary());
        validateContactResult(bo.getContactResult());
        TalentFollowUp entity = MapstructUtils.convert(bo, TalentFollowUp.class);
        if (entity == null) {
            entity = new TalentFollowUp();
        }
        // 服务端权威字段：主键、人才ID、跟进人均不接受前端写入
        entity.setFollowId(null);
        entity.setTalentId(talentId);
        entity.setFollowerId(currentUserId());
        entity.setContactTime(bo.getContactTime() == null ? LocalDateTime.now() : bo.getContactTime());
        // 跟进只写 hr_talent_follow_up：绝不写 hr_recruit_stage_log，也不触发应聘阶段流转（§8.16）
        talentFollowUpMapper.insert(entity);
        // 同步主档「最近联系时间 / 下次联系时间」，让 §8.17 的「按最近联系时间检索」真正可用
        syncProfileFollowTime(talentId);
        // 只记录主键与人才ID，不把沟通摘要写入应用日志
        log.info("新增人才跟进记录, followId={}, talentId={}", entity.getFollowId(), talentId);
        return entity.getFollowId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(TalentFollowUpBo bo) {
        if (bo == null || bo.getFollowId() == null) {
            throw new ServiceException("跟进记录ID不能为空");
        }
        TalentFollowUp exist = requireFollowUp(bo.getFollowId());
        talentProfileService.requireVisible(exist.getTalentId());
        validateSummary(bo.getSummary());
        validateContactResult(bo.getContactResult());
        TalentFollowUp update = MapstructUtils.convert(bo, TalentFollowUp.class);
        if (update == null) {
            update = new TalentFollowUp();
        }
        update.setFollowId(exist.getFollowId());
        // 人才归属不可通过编辑迁移；跟进人不接受前端改写
        update.setTalentId(exist.getTalentId());
        update.setFollowerId(exist.getFollowerId());
        int rows = talentFollowUpMapper.updateById(update);
        if (rows == 0) {
            throw new ServiceException("跟进记录不存在或已删除");
        }
        // 联系时间/下次联系时间可能被修改，重算主档冗余字段
        syncProfileFollowTime(exist.getTalentId());
        log.info("更新人才跟进记录, followId={}, talentId={}", exist.getFollowId(), exist.getTalentId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long[] followIds) {
        if (followIds == null || followIds.length == 0) {
            return;
        }
        List<Long> ids = Arrays.stream(followIds).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        List<Long> talentIds = new ArrayList<>(ids.size());
        for (Long id : ids) {
            TalentFollowUp exist = requireFollowUp(id);
            talentProfileService.requireVisible(exist.getTalentId());
            talentIds.add(exist.getTalentId());
        }
        talentFollowUpMapper.deleteByIds(ids);
        // 删除后重算主档冗余字段：该人才可能已无任何跟进记录
        talentIds.stream().distinct().forEach(this::syncProfileFollowTime);
        log.info("逻辑删除人才跟进记录, followIds={}", ids);
    }

    @Override
    public void validateSummary(String summary) {
        if (StringUtils.isBlank(summary)) {
            return;
        }
        if (summary.length() > SUMMARY_MAX_LENGTH) {
            throw new ServiceException(MSG_SUMMARY_TOO_LONG);
        }
        if (ID_CARD.matcher(summary).find() || BANK_CARD.matcher(summary).find()) {
            throw new ServiceException(MSG_SUMMARY_SENSITIVE);
        }
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (summary.contains(keyword)) {
                throw new ServiceException(MSG_SUMMARY_SENSITIVE);
            }
        }
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 加载跟进记录，不存在时抛中文提示。
     *
     * @param followId 跟进记录ID
     * @return 跟进记录实体
     */
    private TalentFollowUp requireFollowUp(Long followId) {
        if (followId == null) {
            throw new ServiceException("跟进记录ID不能为空");
        }
        TalentFollowUp entity = talentFollowUpMapper.selectById(followId);
        if (entity == null) {
            throw new ServiceException("跟进记录不存在或已删除");
        }
        return entity;
    }

    /**
     * 校验联系结果编码，未知编码一律拒绝（fail-safe）。
     *
     * @param contactResult 联系结果编码，可为空
     */
    private void validateContactResult(String contactResult) {
        if (StringUtils.isBlank(contactResult)) {
            return;
        }
        if (ContactResultEnum.find(contactResult) == null) {
            throw new ServiceException("联系结果不合法，请使用字典 talent_contact_result 的编码"
                + "（connected/no_answer/refused/interested/follow_up_later/invalid）");
        }
    }

    /**
     * 同步人才主档冗余字段 {@code last_follow_time}（最近联系时间）与 {@code next_follow_time}（下次联系时间）。
     *
     * <p><b>为什么必须写</b>：设计文档 §8.12 把这两列定义为主档字段，§8.17 要求支持
     * 「按最近联系时间」检索；若跟进不更新主档，该检索条件将永远查不到结果。</p>
     *
     * <p><b>本类是主档这两列的第二个写入方</b>（第一个是人才主档自身的创建/编辑流程）。
     * 为避免与主档编辑的乐观锁事务相互干扰，这里照 {@code TalentResumeServiceImpl}
     * 同步 {@code current_resume_id}/{@code resume_update_time} 的先例：</p>
     * <ul>
     *     <li>使用<b>条件更新</b>（{@code LambdaUpdateWrapper} + {@code update(null, wrapper)}），
     *     只写这两列；{@code update_by}/{@code update_time} 由框架
     *     {@code InjectionMetaObjectHandler} 自动填充；</li>
     *     <li><b>不递增 {@code version}</b>：这两列是展示与检索用的冗余快照，
     *     不属于主档业务字段，不应让用户正在编辑的主档表单产生版本冲突。</li>
     * </ul>
     *
     * <p><b>取值口径</b>：最近联系时间取该人才现存跟进记录中 {@code contact_time} 的最大值
     * （无跟进记录时置空）；下次联系时间取 {@code contact_time} 最大那条记录的
     * {@code next_contact_time}，与列表页默认排序（按联系时间倒序）口径一致。</p>
     *
     * @param talentId 人才主档ID
     */
    private void syncProfileFollowTime(Long talentId) {
        if (talentId == null) {
            return;
        }
        TalentFollowUp latest = talentFollowUpMapper.selectOne(new LambdaQueryWrapper<TalentFollowUp>()
            .eq(TalentFollowUp::getTalentId, talentId)
            .orderByDesc(TalentFollowUp::getContactTime)
            .orderByDesc(TalentFollowUp::getFollowId)
            .last("LIMIT 1"));
        LambdaUpdateWrapper<TalentProfile> wrapper = new LambdaUpdateWrapper<TalentProfile>()
            .eq(TalentProfile::getTalentId, talentId)
            .set(TalentProfile::getLastFollowTime, latest == null ? null : latest.getContactTime())
            .set(TalentProfile::getNextFollowTime, latest == null ? null : latest.getNextContactTime());
        talentProfileMapper.update(null, wrapper);
    }

    /**
     * 取当前登录用户ID；无登录态返回 null。
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

}
