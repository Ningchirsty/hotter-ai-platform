package org.dromara.hrtalent.service.talent;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.talent.CandidateCreateBo;
import org.dromara.hrtalent.domain.bo.talent.CandidateQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentPrecheckBo;
import org.dromara.hrtalent.domain.vo.talent.CandidateVo;
import org.dromara.hrtalent.domain.vo.talent.TalentPrecheckVo;

/**
 * 候选人服务接口（SPEC-P3 §2.1、§3.1）。
 *
 * <p><b>一人一档</b>：候选人不是独立实体，本服务只提供「有应聘记录的人才视图」查询、
 * 查重后创建（新建或复用主档 + 可选首条应聘记录）以及电话明文查看。</p>
 *
 * @author hr-talent
 */
public interface ICandidateService {

    /**
     * 候选人列表（自动套人才可见范围）。
     *
     * @param bo        检索条件
     * @param pageQuery 分页参数
     * @return 候选人分页结果（电话/邮箱已脱敏）
     */
    PageResult<CandidateVo> queryPage(CandidateQueryBo bo, PageQuery pageQuery);

    /**
     * 新增候选人：先查重，再做「新建主档」或「复用已有主档 + 追加应聘记录」。
     *
     * @param bo 候选人入参
     * @return 人才主档ID
     */
    Long create(CandidateCreateBo bo);

    /**
     * 候选人重复预检（与人才主档预检同口径）。
     *
     * @param bo 预检入参
     * @return 分级预检结果
     */
    TalentPrecheckVo precheck(TalentPrecheckBo bo);

    /**
     * 断言该ID存在有效应聘记录（候选人身份判定）。
     *
     * @param talentId 人才主档ID
     */
    void assertCandidate(Long talentId);

    /**
     * 取候选人电话明文（<b>先写审计再返发明文</b>）。
     *
     * <p>实现必须遵守 SPEC-P3 §3.6：用途为空时直接拒绝并记录 {@code denied}；
     * 用途非空时先调用 {@code SensitiveAuditRecorder.record(phone_view, talent, ...)}
     * 写入审计，再解密返回电话明文。</p>
     *
     * @param talentId 人才主档ID
     * @param purpose  查看用途/事由（必填）
     * @return 电话明文；未登记电话时返回 null
     */
    String viewPhone(Long talentId, String purpose);

}
