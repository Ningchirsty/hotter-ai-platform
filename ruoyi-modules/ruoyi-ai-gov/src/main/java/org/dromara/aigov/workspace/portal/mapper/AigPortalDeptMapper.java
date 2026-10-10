package org.dromara.aigov.workspace.portal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 门户用的只读组织查询（主文档线增量 2）。
 *
 * <h3>为什么是一个只读的小查询，而不是加模块依赖</h3>
 * <p>门户需要"用户所在部门的祖级链"来匹配按组织定向的岗位绑定。为此把整个
 * {@code ruoyi-system} 模块拖进来是不划算的（依赖会一路扩散）；这里只需要读
 * {@code sys_dept} 一个字段。查询<b>只读</b>且只取 {@code ancestors}，
 * 不碰部门写路径，也不改变任何数据权限语义（F-05：岗位可见性只是可见性过滤，
 * 真实数据权仍由既有数据权限决定）。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigPortalDeptMapper {

    /**
     * 读取部门的祖级列表（逗号分隔）。
     *
     * @param deptId 部门ID
     * @return 祖级列表；部门不存在或已删除时返回 null
     */
    @Select("select ancestors from sys_dept where dept_id = #{deptId} and del_flag = '0'")
    String selectAncestors(@Param("deptId") Long deptId);

}
