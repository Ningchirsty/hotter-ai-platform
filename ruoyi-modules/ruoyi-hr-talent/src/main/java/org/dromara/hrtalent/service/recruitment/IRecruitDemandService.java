package org.dromara.hrtalent.service.recruitment;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitDemandActionBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitDemandBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitDemandQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitDemandChangeVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitDemandVo;

import java.util.List;

/**
 * 招聘需求服务接口（SPEC-P2 §3.1 / §4.1）。
 *
 * <p><b>职责边界</b>：本服务是招聘需求状态机与变更留痕的唯一权威入口；
 * 权限串校验、数据权限、乐观锁与派生字段计算均在此收敛，Controller 只做参数组装。</p>
 *
 * @author hr-talent
 */
public interface IRecruitDemandService {

    /**
     * 分页查询招聘需求（自动应用数据权限）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 招聘需求分页结果
     */
    PageResult<RecruitDemandVo> queryPage(RecruitDemandQueryBo bo, PageQuery pageQuery);

    /**
     * 查询招聘需求详情。
     *
     * @param demandId 需求ID
     * @return 需求详情（含派生字段待招聘人数）
     */
    RecruitDemandVo getDetail(Long demandId);

    /**
     * 新增招聘需求草稿。
     *
     * @param bo 需求入参
     * @return 新增的需求ID
     */
    Long create(RecruitDemandBo bo);

    /**
     * 更新招聘需求（乐观锁）。
     * <p>进入招聘中之后修改关键字段会追加一条变更历史。</p>
     *
     * @param bo 需求入参（必须携带 version）
     */
    void update(RecruitDemandBo bo);

    /**
     * 逻辑删除招聘需求（仅草稿允许删除）。
     *
     * @param demandIds 需求ID集合
     */
    void remove(Long[] demandIds);

    /**
     * 执行状态动作。
     *
     * @param demandId 需求ID
     * @param action   动作编码：submit/confirm/pause/resume/complete/close
     * @param bo       动作入参（暂停、复开、关闭必须填写原因）
     */
    void action(Long demandId, String action, RecruitDemandActionBo bo);

    /**
     * 查询招聘需求变更历史（按操作时间倒序）。
     *
     * @param demandId 需求ID
     * @return 变更历史列表
     */
    List<RecruitDemandChangeVo> queryChanges(Long demandId);

}
