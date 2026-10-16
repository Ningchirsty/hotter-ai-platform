package org.dromara.aigov.changerequest.service;

import org.dromara.aigov.changerequest.domain.bo.AigChangeRequestBo;
import org.dromara.aigov.changerequest.domain.vo.AigChangeRequestVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * 高风险变更单（Q1C 完整版）。
 *
 * <p><b>两步成立、批准即生效</b>：申请人（{@code aig:capability:edit}）填目标值提一张单，
 * 审批人（{@code aig:capability:high-risk}）批准 → **当场执行** → 落 before/after 与生效时间。
 * 执行前做**乐观并发校验**：现场与申请时不一致就置为 {@code CONFLICTED}，不覆盖别人的改动。</p>
 *
 * @author ai-gov
 */
public interface IAigChangeRequestService {

    /**
     * 提交变更单。
     *
     * @param bo       申请内容（含目标值）
     * @param actorId  申请人用户ID
     * @param actorName 申请人账号（冗余留痕）
     * @return 变更单ID
     */
    Long submit(AigChangeRequestBo bo, Long actorId, String actorName);

    /**
     * 分页查询变更单。
     *
     * @param bo        查询条件（能力/状态/只看我的）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<AigChangeRequestVo> queryPage(AigChangeRequestBo bo, PageQuery pageQuery);

    /**
     * 批准并**当场执行**。
     *
     * <p>执行走既有服务方法（因此 Q1C 闸门仍生效——批准人持有 high-risk，闸门放行）。
     * 执行前用 before 快照与当前行逐字段比对；不一致 → 置 {@code CONFLICTED}。</p>
     *
     * <p><b>为什么返回结果而不是抛异常</b>：冲突/执行失败都要**留下这一行**（那是留痕事实），
     * 而抛异常会让"写入 CONFLICTED"随事务一起回滚——于是页面上什么都没有，也没人知道发生过什么。
     * 所以这里把结果返回给调用方，由它展示原因。</p>
     *
     * @param changeRequestId 变更单ID
     * @param remark          审批意见
     * @param approverId      批准人用户ID（**不得等于申请人**）
     * @param approverName    批准人账号
     * @return 处理后的变更单（status 可能是 APPLIED 或 CONFLICTED，后者看 resultRemark）
     */
    AigChangeRequestVo approve(Long changeRequestId, String remark, Long approverId, String approverName);

    /**
     * 驳回（不改动任何配置）。
     *
     * @param changeRequestId 变更单ID
     * @param remark          驳回意见（必填）
     * @param approverId      审批人用户ID
     * @param approverName    审批人账号
     */
    void reject(Long changeRequestId, String remark, Long approverId, String approverName);

    /**
     * 撤回（只有申请人本人可以撤，且只能在待批准时）。
     *
     * @param changeRequestId 变更单ID
     * @param actorId         操作人用户ID
     */
    void cancel(Long changeRequestId, Long actorId);
}
