package org.dromara.aigov.workspace.portal.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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

}
