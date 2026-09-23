package org.dromara.hrtalent.service.recruitment;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitStandardBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitStandardQueryBo;
import org.dromara.hrtalent.domain.entity.RecruitStandard;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitStandardVo;

import java.time.LocalDate;

/**
 * 招聘期限标准服务。
 *
 * <p><b>业务键</b>：{@code 岗位名称 + 公司 + 生效日期}。表上没有唯一索引（集团通用条目的公司为空，
 * 且生效期允许演进），所以「同键只能有一条」这条规则由本服务保证：手工新增撞键直接拒绝，
 * 导入撞键则按「覆盖该条的天数/失效日/状态」处理，并把这件事作为提示回显给用户。</p>
 *
 * @author hr-talent
 */
public interface IRecruitStandardService {

    /**
     * 分页查询。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<RecruitStandardVo> queryPage(RecruitStandardQueryBo bo, PageQuery pageQuery);

    /**
     * 详情。
     *
     * @param standardId 标准ID
     * @return 详情
     */
    RecruitStandardVo getDetail(Long standardId);

    /**
     * 新增（同业务键已存在时拒绝，并提示改走编辑）。
     *
     * @param bo 入参
     * @return 标准ID
     */
    Long create(RecruitStandardBo bo);

    /**
     * 修改。
     *
     * @param bo 入参
     */
    void update(RecruitStandardBo bo);

    /**
     * 删除（逻辑删除）。
     *
     * @param standardId 标准ID
     */
    void remove(Long standardId);

    /**
     * 按业务键查找。
     *
     * @param jobName       岗位名称
     * @param companyDeptId 公司（平台部门）ID，可为空表示集团通用
     * @param effectiveDate 生效日期，可为空
     * @return 命中的标准；不存在返回 null
     */
    RecruitStandard findByKey(String jobName, Long companyDeptId, LocalDate effectiveDate);

    /**
     * 按业务键 upsert（导入专用）：命中则覆盖天数/失效日/状态，未命中则新增。
     *
     * @param bo 入参
     * @return 标准ID
     */
    Long upsertByImport(RecruitStandardBo bo);

}
