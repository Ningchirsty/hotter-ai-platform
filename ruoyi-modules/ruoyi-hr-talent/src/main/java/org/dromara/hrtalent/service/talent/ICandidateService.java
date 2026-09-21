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
     * <p><b>落空也要留痕，且对外不可区分</b>（§15.2、§6）：候选人不存在（无应聘记录）与
     * 人才不存在 / 不可见<b>走同一条落空处置</b>——写同一条 {@code denied} 审计
     * （事件类型、业务对象、结果完全一致），并抛出<b>同一个</b>提示「候选人不存在或已删除」。
     * 这是<b>有意</b>的不可区分：一次被拒绝的电话明文访问往往就是越权探测，既必须留痕，
     * 也不能用错误文案差异让攻击者判断某个候选人是否存在。</p>
     *
     * <p><b>诊断能力保留在审计明细</b>：两条落空分支只有 {@code detailJson} 的 {@code reason} 不同
     * （{@code no_application} / {@code out_of_scope_or_absent}），供运维区分「数据未录入」与
     * 「权限配置 / 错误ID」两种相反的处置方向；明细只承载纯枚举原因码，不含任何敏感内容。</p>
     *
     * @param talentId 人才主档ID
     * @param purpose  查看用途/事由（必填）
     * @return 电话明文；未登记电话时返回 null
     */
    String viewPhone(Long talentId, String purpose);

}
