package org.dromara.hrtalent.service.recruitment;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanItemActionBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanItemBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanItemQueryBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanItemVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanVo;
import org.dromara.hrtalent.domain.vo.recruitment.RolloverChainVo;

/**
 * 公司月度计划与计划任务服务（SPEC-P2 §3.2 / §4.2）。
 *
 * <p><b>两条不可违背的规则</b>：</p>
 * <ol>
 *     <li>一个公司 + 一个自然月只有一张表头（{@code company_dept_id + plan_month} 唯一）。</li>
 *     <li>每次新增招聘任务都创建新记录，即使公司、部门、岗位、人数完全相同也绝不合并；
 *     相似性只通过 {@link #similar} 提示。</li>
 * </ol>
 *
 * @author hr-talent
 */
public interface IRecruitPlanService {

    /**
     * 分页查询月度计划表头。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 表头分页
     */
    PageResult<RecruitPlanVo> queryPage(RecruitPlanQueryBo bo, PageQuery pageQuery);

    /**
     * 查询月度计划表头详情。
     *
     * @param planId 计划ID
     * @return 表头详情
     */
    RecruitPlanVo getDetail(Long planId);

    /**
     * 新建月度计划表头。
     *
     * <p>同一公司同一月份已存在表头时拒绝并给出中文提示（由服务层判定，数据库唯一索引兜底）。</p>
     *
     * @param bo 表头入参
     * @return 新增的计划ID
     */
    Long create(RecruitPlanBo bo);

    /**
     * 修改月度计划表头（仅草稿可改，且不允许修改公司与计划月份）。
     *
     * @param bo 表头入参
     */
    void update(RecruitPlanBo bo);

    /**
     * 执行表头动作（{@code confirm} 确认 / {@code close} 关闭）。
     *
     * @param planId 计划ID
     * @param action 动作编码
     */
    void action(Long planId, String action);

    /**
     * 跨计划分页查询计划任务。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 任务分页
     */
    PageResult<RecruitPlanItemVo> queryItemPage(RecruitPlanItemQueryBo bo, PageQuery pageQuery);

    /**
     * 分页查询指定表头下的计划任务。
     *
     * @param planId    计划表头ID
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 任务分页
     */
    PageResult<RecruitPlanItemVo> queryItemPageOfPlan(Long planId, RecruitPlanItemQueryBo bo, PageQuery pageQuery);

    /**
     * 在指定表头下新增独立计划任务（绝不与相似任务合并）。
     *
     * @param planId 计划表头ID
     * @param bo     任务入参
     * @return 新增的任务ID
     */
    Long addItem(Long planId, RecruitPlanItemBo bo);

    /**
     * 编辑计划任务（仅允许调整人数、负责人、紧急程度、期限标准天数、结转开关等字段）。
     *
     * @param bo 任务入参
     */
    void updateItem(RecruitPlanItemBo bo);

    /**
     * 执行计划任务动作（{@code pause} 暂停 / {@code resume} 恢复 / {@code cancel} 取消）。
     *
     * @param itemId 计划任务ID
     * @param action 动作编码
     * @param bo     动作入参（暂停与取消必须填写原因）
     */
    void itemAction(Long itemId, String action, RecruitPlanItemActionBo bo);

    /**
     * 查询计划任务的跨月结转链。
     *
     * @param itemId 计划任务ID
     * @return 结转链
     */
    RolloverChainVo rolloverChain(Long itemId);

    /**
     * 触发单条计划任务状态重算（管理员手工校准）。
     *
     * @param itemId 计划任务ID
     */
    void refreshItemStatus(Long itemId);

    /**
     * 相似计划提示：返回同公司 + 同部门 + 同岗位的历史任务，仅供提示，不参与合并。
     *
     * @param bo        查询条件（使用 companyDeptId / useDeptId / jobName / excludeItemId）
     * @param pageQuery 分页参数
     * @return 相似任务分页
     */
    PageResult<RecruitPlanItemVo> similar(RecruitPlanItemQueryBo bo, PageQuery pageQuery);

    /**
     * 按「公司 + 月份」取计划表头，没有就新建（导入专用）。
     *
     * <p>导入子公司月度计划时，用户给的是一张扁平表（公司 + 月份 + 岗位 + 人数），
     * 不应该逼他先逐个公司手工建好表头。本方法把「一个公司一个月只有一张表头」这条规则
     * 收在同一个地方：命中即复用，未命中才建，并且沿用 {@link #create} 的全部校验。</p>
     *
     * @param companyDeptId 公司（平台部门）ID
     * @param companyName   公司名称快照
     * @param planMonth     计划月份（yyyy-MM）
     * @return 计划表头（含「本次是复用还是新建」的标记）
     */
    PlanHeader findOrCreatePlan(Long companyDeptId, String companyName, String planMonth);

    /**
     * 计划表头处理结果。
     *
     * <p>之所以不只返回 ID：导入结果要对用户说清「这次自动建了几张表头、复用了哪几张」。
     * 只回一个 ID 的话，调用方只能猜，最后只能说一句含糊的「已存在则复用，不存在则自动创建」，
     * 而用户真正想知道的是这次到底动没动表头。</p>
     *
     * @param planId  计划表头ID
     * @param created true=本次新建；false=命中已有表头
     */
    record PlanHeader(Long planId, boolean created) {
    }

    /**
     * 新增计划任务并记录来源导入批次（导入专用）。
     *
     * <p>与 {@link #addItem} 的唯一区别是把 {@code importBatchId} 落库，使
     * {@code hr_recruit_plan_item.import_batch_id} 能反向追到批次、文件与操作人。</p>
     *
     * @param planId    计划表头ID
     * @param bo        任务入参
     * @param batchId   导入批次ID
     * @return 新增的任务ID
     */
    Long addImportedItem(Long planId, RecruitPlanItemBo bo, Long batchId);

}
