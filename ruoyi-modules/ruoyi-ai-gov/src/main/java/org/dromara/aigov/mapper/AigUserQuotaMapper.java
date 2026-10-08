package org.dromara.aigov.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.aigov.domain.AigUserQuota;
import org.dromara.aigov.domain.vo.AigUserQuotaVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 调用人均配额 Mapper（{@code aig_user_quota}）。
 *
 * <p>只有单表读写：**用量**来自调用审计表（{@code aig_invocation_audit}），
 * 它的计数语句写在 {@link AigInvocationAuditMapper} 上——一张表只有一个 Mapper，
 * 便于审查「谁在读这张表」。</p>
 *
 * <h3>两个必须绕过逻辑删除的方法（{@link AigUserQuota#getDelFlag()} 上是 {@code @TableLogic}）</h3>
 * <p>表的唯一键是 {@code uk_aig_user_quota_user (user_id)}——**只含 user_id，不含 del_flag**，
 * 也就是说「一个人这辈子只能有一行配额」。而 {@code deleteById} 是逻辑删除，会留下一行
 * {@code del_flag='1'} 的墓碑：此后 {@code save()} 查不到它（被逻辑删除过滤掉）却又插不进去
 * （撞唯一键 → 409「数据库中已存在该记录」），于是**删了配额就再也配不上**。</p>
 * <p>修法是「复活那一行」而不是插新行，因此需要两个 MyBatis-Plus 管不到的原生语句：
 * 按 user_id 连已删除行一起查（{@link #selectAnyByUser}），以及把 del_flag 置回 '0'
 * （{@link #restoreById}）。自定义 SQL 不受逻辑删除改写影响，这正是这里需要它的原因。</p>
 *
 * @author ai-gov
 */
public interface AigUserQuotaMapper extends BaseMapperPlus<AigUserQuota, AigUserQuotaVo> {

    /**
     * 按用户取配额行，<b>包含已被逻辑删除的</b>（一人一行，因此最多一行）。
     *
     * <p>供「复活」判断用：{@code selectOne} 会带上 {@code del_flag='0'}，看不到墓碑。</p>
     *
     * @param userId 用户ID
     * @return 配额行（含已删）；无则 null
     */
    @Select("select quota_id, user_id, user_name, daily_limit, monthly_limit, status, del_flag, "
        + "create_dept, create_by, create_time, update_by, update_time, remark "
        + "from aig_user_quota where user_id = #{userId} order by quota_id limit 1")
    AigUserQuota selectAnyByUser(@Param("userId") Long userId);

    /**
     * 把一行被逻辑删除的配额复活（{@code del_flag} 置回 '0'）。
     *
     * <p>只翻这一个字段：随后的更新仍走 {@code updateById}，让 {@code update_by/update_time}
     * 由平台的自动填充写入——自定义 SQL 不走自动填充，字段写多了反而会漏。</p>
     *
     * @param quotaId 配额ID
     * @return 影响行数
     */
    @Update("update aig_user_quota set del_flag = '0' where quota_id = #{quotaId}")
    int restoreById(@Param("quotaId") Long quotaId);
}
