package org.dromara.aigov.agent.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.aigov.agent.domain.AigEvaluationRun;
import org.dromara.aigov.agent.domain.vo.AigEvaluationRunVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 评测运行 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigEvaluationRunMapper extends BaseMapperPlus<AigEvaluationRun, AigEvaluationRunVo> {

    /**
     * 查该用例被<b>逻辑删除</b>的最近一次评测运行。
     *
     * <p><b>为什么需要绕过 {@code @TableLogic}</b>：{@code aig_evaluation_run.del_flag} 是逻辑删除，
     * 所有走 Mapper 的查询都会自动带上 {@code del_flag='0'}。实测（真库探针）表明：把最新那次
     * <b>FAIL</b> 的记录逻辑删除之后，「最近一次运行」会<b>回退到更早的那次 PASS</b>——
     * 于是「清理一下失败的评测记录」就成了一条让门槛放行的路。评测报告是证据，
     * 删除记录不该改变结论，因此这里用原生 SQL 把被删的那条找出来，交给证据判定去发现。</p>
     *
     * <p>{@code @InterceptorIgnore(tenantLine = "true")}：原生 SQL 不参与多租户条件注入
     * （本表没有 tenant_id 列，被注入会直接变成「Unknown column」）。</p>
     *
     * @param targetType      评测对象类型
     * @param targetVersionId 对象版本ID
     * @param caseId          用例ID
     * @return 被删除的最近一次运行；没有则返回 null
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("select run_id, run_no, target_type, target_version_id, case_id, result_status, "
        + "review_result, operate_time from aig_evaluation_run "
        + "where del_flag = '1' and target_type = #{targetType} "
        + "and target_version_id = #{targetVersionId} and case_id = #{caseId} "
        + "order by operate_time desc, run_id desc limit 1")
    AigEvaluationRun selectNewestDeletedRun(@Param("targetType") String targetType,
                                            @Param("targetVersionId") Long targetVersionId,
                                            @Param("caseId") Long caseId);

}
