package org.dromara.hrtalent.service.talent;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.talent.TalentPrecheckBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileQueryBo;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.vo.talent.TalentPrecheckVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileChangeVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileDetailVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileVo;

import java.util.List;

/**
 * 人才主档服务接口（SPEC-P3 §2.1 人才主档与查重）。
 *
 * <p><b>授权硬约束</b>：列表与详情<b>必须</b>经
 * {@code org.dromara.hrtalent.domainservice.TalentScopeDomainService} 的可见范围条件，
 * 实现方不得自行拼装授权规则（设计文档 §11.1 / §21.14）。</p>
 *
 * @author hr-talent
 */
public interface ITalentProfileService {

    /**
     * 人才主档组合检索（自动套人才可见范围）。
     *
     * @param bo        检索条件，可为空
     * @param pageQuery 分页参数
     * @return 人才主档分页结果（电话/邮箱已脱敏）
     */
    PageResult<TalentProfileVo> queryPage(TalentProfileQueryBo bo, PageQuery pageQuery);

    /**
     * 按人才导出需要取「可见范围内」的主档实体列表（SPEC-P4 §2.6 F 线内部专用）。
     *
     * <p><b>为什么返回实体</b>：敏感台账需要联系方式明文，而明文只存在于
     * {@code @EncryptField} 解密后的实体上；VO 只承载脱敏串，因此导出必须走实体。</p>
     *
     * <p><b>调用方硬约束</b>：调用方<b>必须</b>先完成「导出按钮权限 → 敏感台账独立权限 →
     * 用途（purpose）校验」三道闸门并写审计，再调用本方法；
     * 本方法自身只负责「叠加人才可见范围（统一走 {@code TalentScopeDomainService}）+ §8.17 组合条件
     * + 条数上限」，不重复实现授权规则（设计文档 §8.17、§11.1）。</p>
     *
     * @param bo    检索条件（P4 §8.17 增强条件与 {@code GET /talent/profiles} 完全同源），可为空
     * @param limit 最大返回条数（必须为正数）
     * @return 主档实体列表（最多 {@code limit} 条，按创建时间倒序）
     */
    List<TalentProfile> searchForExport(TalentProfileQueryBo bo, int limit);

    /**
     * 人才主档详情。
     *
     * @param talentId 人才主档ID
     * @return 人才主档详情（电话/邮箱已脱敏，不含密文）
     */
    TalentProfileDetailVo getDetail(Long talentId);

    /**
     * 人才重复预检（不落库）。
     *
     * @param bo 预检入参
     * @return 分级预检结果
     */
    TalentPrecheckVo precheck(TalentPrecheckBo bo);

    /**
     * 创建人才主档。
     *
     * @param bo            人才主档入参
     * @param precheck      是否执行重复预检（强/中匹配时拒绝静默创建）
     * @param duplicateAck  是否已确认「不是同一人」
     * @param operatorId    操作人用户ID，可为空
     * @return 新建的人才主档ID
     */
    Long create(TalentProfileBo bo, boolean precheck, boolean duplicateAck, Long operatorId);

    /**
     * 更新人才主档（关键字段变更写入变更历史）。
     *
     * @param bo 人才主档入参（必须携带 version）
     */
    void update(TalentProfileBo bo);

    /**
     * 逻辑删除人才主档（仅无应聘记录可删）。
     *
     * @param talentIds 人才主档ID数组
     */
    void remove(Long[] talentIds);

    /**
     * 归档人才主档。
     *
     * @param talentId 人才主档ID
     * @param reason   归档原因，可为空
     */
    void archive(Long talentId, String reason);

    /**
     * 人才关键字段变更历史。
     *
     * @param talentId  人才主档ID
     * @param pageQuery 分页参数
     * @return 变更历史分页结果
     */
    PageResult<TalentProfileChangeVo> queryChanges(Long talentId, PageQuery pageQuery);

    /**
     * 按人才ID取电话密文对应的明文（仅内部使用，调用方必须先完成鉴权与审计）。
     *
     * <p><b>本方法不写审计</b>：审计由调用方（电话明文查看接口）在拿到明文之前完成。</p>
     *
     * @param talentId 人才主档ID
     * @return 电话明文；未登记电话时返回 null
     */
    String getPhonePlain(Long talentId);

    /**
     * 查看人才电话明文（人才档案域专用接口，对应 {@code POST /talent/profiles/{id}/phone-view}）。
     *
     * <p><b>与候选人侧 {@code POST /recruit/candidates/{id}/phone-view} 的区别</b>：
     * 候选人侧先执行 {@code assertCandidate}（要求该人才存在应聘记录），
     * 因此<b>无应聘记录的人才无法查看电话明文</b>；本方法只做人才主档资源级鉴权，
     * <b>不要求有应聘记录</b>（设计文档 §8.12 已将电话列为人才主档字段）。</p>
     *
     * <p><b>执行顺序</b>（与简历下载同口径）：用途校验（为空 → 写 {@code denied} 审计并拒绝）
     * → 资源级鉴权（唯一权威 {@code TalentScopeDomainService}）→ 写审计 → 返回明文；
     * 明文<b>绝不</b>写入日志。电话密文由 {@code @EncryptField} 自动解密，本方法直接返回解密后的值。</p>
     *
     * @param talentId 人才主档ID
     * @param purpose  查看事由（必填）
     * @return 电话明文；未登记电话时返回 null
     */
    String viewPhone(Long talentId, String purpose);

    /**
     * 校验人才可见性（供候选人域复用，避免各处重复实现授权规则）。
     *
     * @param talentId 人才主档ID
     * @return 可见的人才主档
     */
    org.dromara.hrtalent.domain.entity.TalentProfile requireVisible(Long talentId);

    /**
     * 判断人才是否已存在有效应聘记录。
     *
     * @param talentId 人才主档ID
     * @return 是否存在
     */
    boolean hasApplication(Long talentId);

    /**
     * 按事件刷新人才状态（供应聘阶段/报到事件监听器调用，设计文档 §7.6.4）。
     *
     * <p>只允许在下列状态之间流转，避免事件乱序把人工设置的「禁止联系 / 受限 / 已归档」覆盖掉：
     * {@code draft / active / reserved / recruiting ⇄ recruiting}，以及
     * {@code recruiting → hired}。其余状态一律跳过。</p>
     *
     * @param talentId    人才主档ID
     * @param targetStatus 目标状态编码
     * @param reason      状态原因，可为空
     * @return 是否发生了状态变化
     */
    boolean refreshStatusByEvent(Long talentId, String targetStatus, String reason);

}
