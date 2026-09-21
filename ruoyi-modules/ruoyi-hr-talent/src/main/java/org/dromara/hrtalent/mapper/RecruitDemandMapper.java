package org.dromara.hrtalent.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.annotation.DataColumn;
import org.dromara.common.mybatis.annotation.DataPermission;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitDemand;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitDemandVo;

/**
 * 招聘需求 Mapper 接口。
 *
 * <p>分页查询通过 {@link DataPermission} 自动追加数据权限条件
 * （按公司部门维度过滤，SPEC-P2 §3.1）。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface RecruitDemandMapper extends BaseMapperPlus<RecruitDemand, RecruitDemandVo> {

    /**
     * 分页查询招聘需求（自动应用数据权限）。
     *
     * @param page          分页信息
     * @param queryWrapper  查询条件
     * @return 招聘需求分页结果
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "company_dept_id")
    })
    default Page<RecruitDemandVo> selectPageDemandList(Page<RecruitDemand> page, Wrapper<RecruitDemand> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }

}
