package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitInterview;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitInterviewVo;

import java.util.Collection;
import java.util.List;

/**
 * 面试记录 Mapper 接口。
 *
 * <p>常规增删改查使用 MyBatis-Plus 通用能力与 Lambda 条件构造器；
 * 仅候选人摘要需要跨表只读关联（{@code hr_recruit_application} + {@code hr_talent_profile}
 * + {@code hr_recruit_job}），故以只读 {@code @Select} 提供，<b>不写</b>其它域的表。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface RecruitInterviewMapper extends BaseMapperPlus<RecruitInterview, RecruitInterviewVo> {

    /**
     * 批量读取应聘记录摘要（只读），用于列表与详情带出候选人/岗位信息。
     *
     * <p>返回对象只填充以下字段，其余字段为 null：
     * {@code applicationId}、{@code applicationNo}、{@code candidateName}、
     * {@code jobName}、{@code planItemId}、{@code currentStage}。</p>
     *
     * @param applicationIds 应聘记录ID集合，调用方保证非空
     * @return 应聘记录摘要列表，按应聘记录ID去重
     */
    @Select("""
        <script>
        select a.application_id  as applicationId,
               a.application_no  as applicationNo,
               t.name            as candidateName,
               j.job_name        as jobName,
               j.plan_item_id    as planItemId,
               a.current_stage   as currentStage
        from hr_recruit_application a
        left join hr_talent_profile t on t.talent_id = a.talent_id and t.del_flag = '0'
        left join hr_recruit_job j on j.job_id = a.job_id and j.del_flag = '0'
        where a.del_flag = '0'
          and a.application_id in
          <foreach collection="applicationIds" item="applicationId" open="(" separator="," close=")">
              #{applicationId}
          </foreach>
        </script>
        """)
    List<RecruitInterviewVo> selectApplicationSummaries(@Param("applicationIds") Collection<Long> applicationIds);

}
