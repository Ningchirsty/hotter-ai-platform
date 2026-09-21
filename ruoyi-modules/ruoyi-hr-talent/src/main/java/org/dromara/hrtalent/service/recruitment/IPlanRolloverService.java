package org.dromara.hrtalent.service.recruitment;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.recruitment.RolloverExecuteBo;
import org.dromara.hrtalent.domain.bo.recruitment.RolloverPreviewBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanRolloverVo;
import org.dromara.hrtalent.domain.vo.recruitment.RolloverPreviewVo;

import java.util.List;

/**
 * 月度结转服务（SPEC-P2 §3.3 / §4.3）。
 *
 * <p>结转必须<b>幂等</b>：同一来源任务对同一目标月份最多生成一条结转任务；
 * 单条失败记录原因并允许安全重试，不因一条失败丢掉整批已成功的条目。</p>
 *
 * @author hr-talent
 */
public interface IPlanRolloverService {

    /**
     * 预览某来源月份到目标月份的待结转任务、人数与异常项（只读，不写库）。
     *
     * @param bo 预览条件
     * @return 预览结果
     */
    RolloverPreviewVo preview(RolloverPreviewBo bo);

    /**
     * 执行月度结转（幂等；正常由定时任务调用，也支持管理员手工触发）。
     *
     * @param bo 执行条件
     * @return 执行结果（含成功 / 跳过 / 失败条数）
     */
    RolloverPreviewVo execute(RolloverExecuteBo bo);

    /**
     * 重试指定批次中失败（或未成功）的结转条目。
     *
     * @param batchNo 结转批次号
     * @return 重试结果
     */
    RolloverPreviewVo retry(String batchNo);

    /**
     * 查询指定批次的结转明细（含异常明细）。
     *
     * @param batchNo 结转批次号
     * @return 结转明细列表
     */
    List<RecruitPlanRolloverVo> getBatch(String batchNo);

    /**
     * 结转记录分页查询（按批次号 / 目标月份 / 执行结果过滤）。
     *
     * <p>P1 DDL 未提供独立批次表，因此批次维度由 {@code hr_recruit_plan_rollover}
     * 明细的 {@code batch_no} 表达：本接口按明细分页返回，每行携带 {@code batchId}/{@code batchNo}，
     * 前端可按批次号分组展示。</p>
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 结转明细分页
     */
    PageResult<RecruitPlanRolloverVo> queryPage(RolloverPreviewBo bo, PageQuery pageQuery);

}
