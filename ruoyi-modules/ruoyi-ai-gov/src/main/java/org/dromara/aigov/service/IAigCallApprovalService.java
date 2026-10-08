package org.dromara.aigov.service;

import org.dromara.aigov.domain.bo.AigCallApprovalBo;
import org.dromara.aigov.domain.bo.AigCallApprovalDecideBo;
import org.dromara.aigov.domain.vo.AigCallApprovalSweepVo;
import org.dromara.aigov.domain.vo.AigCallApprovalVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * 调用授权审批服务（C3：把 {@code aig_route_policy.require_approval} 做实）。
 *
 * <p>流程：申请人提交单子 → 审批人批准 → 得到一张「人 × 能力 × 数据等级」、
 * 带有效期的授权 → 有效期内该组合的调用免再审。</p>
 *
 * <p><b>写方法都显式收「操作人」参数</b>（而不是在实现里读登录态）：与
 * {@code IAigPackageService#install(packageVersionId, operatorId)} 同一做法——
 * 谁在操作只能由控制器从登录态取，服务层保持可单测、也无法被伪造的入参绕过。
 * 控制器传的是 {@code LoginHelper.getUserId()}，客户端请求体里的任何 id 都不作数。</p>
 *
 * @author ai-gov
 */
public interface IAigCallApprovalService {

    /**
     * 分页查询审批单/授权（治理台清单）。
     *
     * <p>查询条件里的 {@code requesterId} 同时被「只看自己的」接口复用——
     * 那种场景由控制器用登录人<b>覆盖</b>它，客户端传值不生效。</p>
     *
     * @param bo        查询条件（能力/数据等级/申请人/审批人/状态）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<AigCallApprovalVo> queryPage(AigCallApprovalBo bo, PageQuery pageQuery);

    /**
     * 提交一张调用授权申请单。
     *
     * <p>只做「能不能申请」的校验（能力存在、该组合的策略确实要求审批、同一组合没有
     * 另一张待审批单），<b>不做任何放行</b>——放行只发生在批准之后。</p>
     *
     * @param requesterId   申请人（来自登录态）
     * @param requesterName 申请人账号（冗余存储，便于离线核对）
     * @param bo            申请内容（能力、数据等级、理由）
     * @return 审批单ID
     */
    Long apply(Long requesterId, String requesterName, AigCallApprovalBo bo);

    /**
     * 批准或驳回一张待审批单。
     *
     * <p>三道校验缺一不可：必须仍是 PENDING（并发下用条件更新兜住）、
     * 不得已过审批时限、<b>审批人不得是申请人本人</b>。批准时按配置算出授权有效期。</p>
     *
     * @param approvalId   审批单ID
     * @param approverId   审批人（来自登录态）
     * @param approverName 审批人账号（冗余存储）
     * @param bo           决定（批准/驳回 + 意见）
     */
    void decide(Long approvalId, Long approverId, String approverName, AigCallApprovalDecideBo bo);

    /**
     * 撤回自己提交的、尚未出结论的申请单。
     *
     * @param approvalId  审批单ID
     * @param requesterId 申请人（来自登录态；只能撤自己的）
     */
    void cancel(Long approvalId, Long requesterId);

    /**
     * 判断某人此刻对某能力某数据等级是否有<b>有效授权</b>
     * （存在 status=APPROVED 且 valid_until &gt; now 的单子）。
     *
     * <p>这是调用入口上的热路径判定，只读、不做任何写操作，也<b>不抛异常</b>：
     * 调用入口需要的只是一个是/否，任何异常都会被它收敛成"没有授权"。</p>
     *
     * @param requesterId    调用人（可为 null：调度/系统发起没有调用人）
     * @param capabilityCode 能力编码
     * @param dataLevel      数据等级
     * @return 有有效授权返回 true
     */
    boolean hasValidGrant(Long requesterId, String capabilityCode, String dataLevel);

    /**
     * 把超过审批时限且仍待审批的单子置为「已超时」。
     *
     * <p>幂等：重复执行不会重复计数（只更新仍是 PENDING 的行）。</p>
     *
     * @return 扫描结果（本次置为超时的条数 + 扫描时刻）
     */
    AigCallApprovalSweepVo expireOverdue();

}
