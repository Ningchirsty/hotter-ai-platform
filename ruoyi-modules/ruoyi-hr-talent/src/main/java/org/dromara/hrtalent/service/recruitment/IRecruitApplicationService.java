package org.dromara.hrtalent.service.recruitment;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.recruitment.ApplicationTransferBo;
import org.dromara.hrtalent.domain.bo.recruitment.ApplicationTransitionBo;
import org.dromara.hrtalent.domain.bo.recruitment.ArrivalRegisterBo;
import org.dromara.hrtalent.domain.bo.recruitment.NoArrivalBo;
import org.dromara.hrtalent.domain.bo.recruitment.OfferRegisterBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitApplicationBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitApplicationQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitApplicationVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitStageLogVo;

import java.util.List;

/**
 * 应聘记录与阶段流转服务（SPEC-P3 §2.2 / §3.2）。
 *
 * <p><b>硬约束</b>：</p>
 * <ul>
 *     <li>每次阶段变化必须写一条 {@code hr_recruit_stage_log}，且与阶段变更在<b>同一事务</b>内完成（§21.7）；</li>
 *     <li>阶段流转必须做乐观锁校验（{@code hr_recruit_application.version}，§9.6）；</li>
 *     <li>进入一面 / 二面 / 待背调 / 待录用 / 待报到的前置校验逐条实现（§7.2），
 *     管理员例外跳转必须填原因并写审计；</li>
 *     <li>登记实际报到时，在同一事务内累加关联月度计划任务的 {@code credited_arrival_qty}
 *     并通过 {@link IPlanItemStatusService#refreshItemStatus(Long)} 刷新状态，
 *     <b>不得</b>自行拼接计划任务状态值（§11.1、§21.7）。</li>
 * </ul>
 *
 * @author hr-talent
 */
public interface IRecruitApplicationService {

    /**
     * 分页查询应聘记录。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 应聘记录分页结果
     */
    PageResult<RecruitApplicationVo> queryPage(RecruitApplicationQueryBo bo, PageQuery pageQuery);

    /**
     * 查询应聘记录详情。
     *
     * @param applicationId 应聘记录ID
     * @return 应聘记录详情
     */
    RecruitApplicationVo getDetail(Long applicationId);

    /**
     * 为已有主档创建应聘记录（同时写入首条阶段历史与计划任务计入关系）。
     *
     * @param bo 应聘记录入参
     * @return 新增的应聘记录ID
     */
    Long create(RecruitApplicationBo bo);

    /**
     * 更新应聘记录的业务快照（不改阶段与结果）。
     *
     * @param bo 应聘记录入参（必须带 version）
     */
    void update(RecruitApplicationBo bo);

    /**
     * 阶段流转（核心）：阶段前进或转入淘汰/放弃/暂缓/人才保留。
     *
     * @param applicationId 应聘记录ID
     * @param bo            流转入参
     */
    void transition(Long applicationId, ApplicationTransitionBo bo);

    /**
     * 转移招聘负责人或调整应聘岗位。
     *
     * @param applicationId 应聘记录ID
     * @param bo            转移入参
     */
    void transfer(Long applicationId, ApplicationTransferBo bo);

    /**
     * 查询阶段历史（按操作时间正序，只读）。
     *
     * @param applicationId 应聘记录ID
     * @return 阶段历史列表
     */
    List<RecruitStageLogVo> listStageLogs(Long applicationId);

    /**
     * 登记邀约结果与计划报到日期（进入「待报到」的必要资料，§7.2 第 5 条）。
     *
     * @param applicationId 应聘记录ID
     * @param bo            邀约登记入参
     */
    void registerOffer(Long applicationId, OfferRegisterBo bo);

    /**
     * 登记实际报到：同一事务内更新应聘记录、累加计划任务到岗人数并刷新任务状态（§7.4/§21.7）。
     *
     * @param applicationId 应聘记录ID
     * @param bo            报到登记入参
     */
    void registerArrival(Long applicationId, ArrivalRegisterBo bo);

    /**
     * 登记未报到及原因（不计入到岗人数）。
     *
     * @param applicationId 应聘记录ID
     * @param bo            未报到登记入参
     */
    void registerNoArrival(Long applicationId, NoArrivalBo bo);

}
