package org.dromara.hrtalent.domainservice;

import org.dromara.common.core.utils.StringUtils;
import org.dromara.hrtalent.domain.vo.talent.TalentPrecheckVo;
import org.dromara.hrtalent.enums.DuplicateMatchLevelEnum;
import org.dromara.hrtalent.mapper.DuplicatePrecheckParams;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.support.TalentContactCodec;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 人才重复检测领域服务（设计文档 §7.6.3 入库规则、§8.4 候选人管理、§21.15 核心算法）。
 *
 * <p><b>分级口径</b>：</p>
 * <ul>
 *     <li><b>强匹配</b>：{@code phone_hash} 或 {@code email_hash} 命中；</li>
 *     <li><b>中匹配</b>：姓名命中，且（当前公司 或 学校 或 简历文件哈希）命中；</li>
 *     <li><b>弱匹配</b>：姓名命中，且期望岗位命中。</li>
 * </ul>
 *
 * <p><b>硬约束</b>：<b>不得</b>以姓名作为唯一判断条件（§8.4）；返回内容只包含可展示摘要，
 * 不包含电话/邮箱明文与任何哈希。</p>
 *
 * <p>本领域服务不落库、不发布事件，只做纯检测；「复用已有主档 / 确认不是同一人 / 提交人工合并」
 * 的处置由调用方（人才主档服务、候选人服务）决定，合并动作属后续阶段。</p>
 *
 * @author hr-talent
 */
@Component
public class TalentDuplicateDomainService {

    /**
     * 命中原因：电话哈希命中。
     */
    private static final String REASON_PHONE = "电话命中";

    /**
     * 命中原因：邮箱哈希命中。
     */
    private static final String REASON_EMAIL = "邮箱命中";

    /**
     * 命中原因：姓名命中。
     */
    private static final String REASON_NAME = "姓名命中";

    /**
     * 命中原因：当前公司命中。
     */
    private static final String REASON_COMPANY = "当前公司命中";

    /**
     * 命中原因：学校命中。
     */
    private static final String REASON_SCHOOL = "学校命中";

    /**
     * 命中原因：简历文件哈希命中。
     */
    private static final String REASON_RESUME = "简历哈希命中";

    /**
     * 命中原因：期望岗位命中。
     */
    private static final String REASON_POSITION = "期望岗位命中";

    /**
     * 简历哈希长度（char(64)）。
     */
    private static final int HASH_LENGTH = 64;

    /**
     * 人才主档 Mapper（只用于预检摘要查询）。
     */
    private final TalentProfileMapper talentProfileMapper;

    /**
     * 构造重复检测领域服务。
     *
     * @param talentProfileMapper 人才主档 Mapper
     */
    public TalentDuplicateDomainService(TalentProfileMapper talentProfileMapper) {
        this.talentProfileMapper = talentProfileMapper;
    }

    /**
     * 执行重复预检（设计文档 §21.15）。
     *
     * @param name             姓名，为空时只做电话/邮箱强匹配
     * @param rawPhone         电话明文，可为空
     * @param rawEmail         邮箱明文，可为空
     * @param currentCompany   当前公司，可为空
     * @param schoolName       学校名称，可为空
     * @param resumeHash       简历文件哈希（64 位十六进制），可为空
     * @param expectedPosition 期望岗位，可为空
     * @return 分级后的预检结果（永不为 null）
     */
    public TalentPrecheckVo precheck(String name,
                                     String rawPhone,
                                     String rawEmail,
                                     String currentCompany,
                                     String schoolName,
                                     String resumeHash,
                                     String expectedPosition) {
        String normalizedName = StringUtils.isBlank(name) ? null : name.trim();
        String normalizedCompany = StringUtils.isBlank(currentCompany) ? null : currentCompany.trim();
        String normalizedSchool = StringUtils.isBlank(schoolName) ? null : schoolName.trim();
        String normalizedPosition = StringUtils.isBlank(expectedPosition) ? null : expectedPosition.trim();
        String normalizedResumeHash = normalizeResumeHash(resumeHash);
        String phoneHash = TalentContactCodec.phoneHash(rawPhone);
        String emailHash = TalentContactCodec.emailHash(rawEmail);

        TalentPrecheckVo vo = new TalentPrecheckVo();
        vo.setNormalizedPhoneHash(phoneHash);
        vo.setNormalizedEmailHash(emailHash);

        DuplicatePrecheckParams params = new DuplicatePrecheckParams(
            normalizedName, normalizedCompany, normalizedSchool,
            normalizedResumeHash, normalizedPosition, phoneHash, emailHash);
        List<TalentProfileMapper.PrecheckRow> rows = talentProfileMapper.selectPrecheckRows(params);
        if (rows == null || rows.isEmpty()) {
            return vo;
        }
        for (TalentProfileMapper.PrecheckRow row : rows) {
            if (row == null) {
                continue;
            }
            classify(vo, row, normalizedName, phoneHash, emailHash,
                normalizedCompany, normalizedSchool, normalizedResumeHash, normalizedPosition);
        }
        vo.setStrongDuplicated(!vo.getStrongMatches().isEmpty());
        vo.setMediumDuplicated(!vo.getMediumMatches().isEmpty());
        vo.setWeakDuplicated(!vo.getWeakMatches().isEmpty());
        vo.setDuplicated(vo.isStrongDuplicated() || vo.isMediumDuplicated() || vo.isWeakDuplicated());
        if (vo.isStrongDuplicated() || vo.isMediumDuplicated()) {
            vo.setMessage(DuplicateMatchLevelEnum.STRONG.getMessage());
        }
        return vo;
    }

    /**
     * 判断结果是否需要人工处置（强/中匹配）。
     *
     * @param vo 预检结果
     * @return 是否需要人工确认或复用已有主档
     */
    public boolean needManualDispose(TalentPrecheckVo vo) {
        return vo != null && (vo.isStrongDuplicated() || vo.isMediumDuplicated());
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 对单个候选行做分级判定，并加入对应集合。
     *
     * @param vo                 预检结果
     * @param row                候选行
     * @param name               规范化姓名
     * @param phoneHash          电话哈希
     * @param emailHash          邮箱哈希
     * @param currentCompany     规范化公司
     * @param schoolName         规范化学校
     * @param resumeHash         规范化简历哈希
     * @param expectedPosition   规范化期望岗位
     */
    private void classify(TalentPrecheckVo vo,
                          TalentProfileMapper.PrecheckRow row,
                          String name,
                          String phoneHash,
                          String emailHash,
                          String currentCompany,
                          String schoolName,
                          String resumeHash,
                          String expectedPosition) {
        boolean phoneHit = phoneHash != null && phoneHash.equals(row.getPhoneHash());
        boolean emailHit = emailHash != null && emailHash.equals(row.getEmailHash());
        if (phoneHit || emailHit) {
            String reason = phoneHit && emailHit ? REASON_PHONE + "、" + REASON_EMAIL
                : (phoneHit ? REASON_PHONE : REASON_EMAIL);
            vo.getStrongMatches().add(item(row, DuplicateMatchLevelEnum.STRONG, reason));
            return;
        }
        boolean nameHit = name != null && name.equals(row.getName());
        if (!nameHit) {
            return;
        }
        boolean companyHit = currentCompany != null && currentCompany.equals(row.getCurrentCompany());
        // 学校与简历哈希的命中事实由 SQL 判定，进入候选行即代表该条件成立
        boolean schoolHit = schoolName != null;
        boolean resumeHit = resumeHash != null;
        if (companyHit || schoolHit || resumeHit) {
            StringBuilder reason = new StringBuilder(REASON_NAME);
            if (companyHit) {
                reason.append(" + ").append(REASON_COMPANY);
            }
            if (schoolHit) {
                reason.append(" + ").append(REASON_SCHOOL);
            }
            if (resumeHit) {
                reason.append(" + ").append(REASON_RESUME);
            }
            vo.getMediumMatches().add(item(row, DuplicateMatchLevelEnum.MEDIUM, reason.toString()));
            return;
        }
        boolean positionHit = expectedPosition != null
            && expectedPosition.equalsIgnoreCase(StringUtils.trim(row.getExpectedPosition()));
        if (positionHit) {
            vo.getWeakMatches().add(item(row, DuplicateMatchLevelEnum.WEAK,
                REASON_NAME + " + " + REASON_POSITION));
        }
    }

    /**
     * 把候选行装配为只含可展示摘要的命中项。
     *
     * @param row   候选行
     * @param level 命中级别
     * @param reason 命中原因
     * @return 命中项
     */
    private TalentPrecheckVo.TalentPrecheckItemVo item(TalentProfileMapper.PrecheckRow row,
                                                       DuplicateMatchLevelEnum level,
                                                       String reason) {
        TalentPrecheckVo.TalentPrecheckItemVo item = new TalentPrecheckVo.TalentPrecheckItemVo();
        item.setTalentId(row.getTalentId());
        item.setTalentNo(row.getTalentNo());
        item.setName(row.getName());
        item.setPhoneMasked(TalentContactCodec.maskPhone(row.getPhoneCipher()));
        item.setEmailMasked(TalentContactCodec.maskEmail(row.getEmailCipher()));
        item.setCurrentCompany(row.getCurrentCompany());
        item.setCurrentCity(row.getCurrentCity());
        item.setExpectedPosition(row.getExpectedPosition());
        item.setTalentStatus(row.getTalentStatus());
        item.setOwnerId(row.getOwnerId());
        item.setOwnerDeptId(row.getOwnerDeptId());
        item.setCreateDate(row.getCreateTime() == null ? null : row.getCreateTime().toLocalDate());
        item.setReason(reason);
        item.setMatchLevel(level.getCode());
        return item;
    }

    /**
     * 规范化简历哈希：只接受 64 位十六进制（char(64) 口径），否则视为未提供。
     *
     * @param resumeHash 原始哈希
     * @return 小写哈希或 null
     */
    private String normalizeResumeHash(String resumeHash) {
        if (StringUtils.isBlank(resumeHash)) {
            return null;
        }
        String value = resumeHash.trim().toLowerCase();
        if (value.length() != HASH_LENGTH || !value.matches("[0-9a-f]{64}")) {
            return null;
        }
        return value;
    }

}
