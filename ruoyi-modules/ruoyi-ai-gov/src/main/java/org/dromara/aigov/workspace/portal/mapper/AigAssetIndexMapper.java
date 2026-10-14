package org.dromara.aigov.workspace.portal.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.aigov.workspace.portal.domain.AigAssetIndex;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 资产聚合索引 Mapper（增量 11）。
 *
 * <p>泛型第二参数是实体自己（T == V）：本处只需要数据访问与一次物理删除。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigAssetIndexMapper extends BaseMapperPlus<AigAssetIndex, AigAssetIndex> {

    /**
     * 物理删除某用户某域的索引行（重建的第一步）。
     *
     * <p><b>刻意物理删除</b>：本表是派生缓存，不是账本。若改成逻辑删除，旧行会继续占着
     * 唯一键 {@code (user_id, domain, asset_id)}，"重建同一对"就会被唯一约束挡住——
     * 而删除后的行又查不到，报错会变成一句没法解释的"重复"。</p>
     *
     * @param userId 用户ID
     * @param domain 域编码
     * @return 删除行数
     */
    @Delete("delete from aig_asset_index where user_id = #{userId} and domain = #{domain}")
    int deleteByUserAndDomain(@Param("userId") long userId, @Param("domain") String domain);

    /**
     * 取某用户某域已同步到的最大资产ID（增量同步的水位线）。
     *
     * <p>各域端口都按<b>资产 ID 倒序</b>返回（内容/视频/图像三个实现的 SQL 都是
     * {@code ORDER BY <id> DESC}），所以"索引里最大的 asset_id"就是"已经同步过的最新一条"，
     * 比它大的就是新增。返回 null 表示该域还没同步过（此时不能按水位线增量，退回全量）。</p>
     *
     * @param userId 用户ID
     * @param domain 域编码
     * @return 最大 asset_id；无行返回 null
     */
    @Select("select max(asset_id) from aig_asset_index where user_id = #{userId} and domain = #{domain}")
    Long selectMaxAssetId(@Param("userId") long userId, @Param("domain") String domain);

}
