package org.dromara.hrtalent.service.recruitment;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitJobAssignBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitJobBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitJobQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitJobVo;

/**
 * 招聘岗位执行项服务接口。
 * <p>岗位是乐观锁聚合根：更新必须携带 {@code version}，冲突时抛出中文提示，
 * 由调用方刷新后重试（设计文档 §9.6）。</p>
 *
 * @author hr-talent
 */
public interface IRecruitJobService {

    /**
     * 分页查询岗位执行项。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 岗位分页结果
     */
    PageResult<RecruitJobVo> queryPage(RecruitJobQueryBo bo, PageQuery pageQuery);

    /**
     * 获取岗位详情。
     *
     * @param jobId 岗位执行项ID
     * @return 岗位详情
     */
    RecruitJobVo getDetail(Long jobId);

    /**
     * 新增岗位执行项。
     * <p>岗位编号由服务端生成，初始状态为草稿（或按入参直接发布），薪资与枚举按业务规则校验。</p>
     *
     * @param bo 岗位入参
     * @return 新增的岗位执行项ID
     */
    Long create(RecruitJobBo bo);

    /**
     * 更新岗位执行项（乐观锁）。
     *
     * @param bo 岗位入参，必须携带 {@code version}
     */
    void update(RecruitJobBo bo);

    /**
     * 批量逻辑删除岗位执行项。
     *
     * @param jobIds 岗位执行项ID数组
     */
    void remove(Long[] jobIds);

    /**
     * 执行岗位动作。
     * <p>支持 {@code close}（关闭）与 {@code reopen}（重新开放）；非法流转给出中文提示。</p>
     *
     * @param jobId  岗位执行项ID
     * @param action 动作编码：close / reopen
     */
    void action(Long jobId, String action);

    /**
     * 分配岗位人员。
     * <p><b>只允许</b>调整招聘负责人、协助人、一面面试官与二面面试官四类字段，
     * 不影响岗位状态、名称、薪资等其它字段。一面/二面面试官是岗位上的计划默认值，
     * 每轮面试的实际面试官归属 {@code hr_recruit_interviewer}（后续阶段实现）。</p>
     *
     * @param bo 分配入参
     */
    void assign(RecruitJobAssignBo bo);

}
