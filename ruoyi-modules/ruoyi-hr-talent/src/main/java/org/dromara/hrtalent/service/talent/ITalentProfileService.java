package org.dromara.hrtalent.service.talent;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.talent.TalentPrecheckBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentPrecheckVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileChangeVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileDetailVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileVo;

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
