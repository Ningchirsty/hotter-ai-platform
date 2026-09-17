package org.dromara.talent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.talent.domain.TlTalentAccessGrant;

import java.util.Collection;
import java.util.List;

/**
 * 人才单条访问授权 Mapper 接口
 * <p>说明：实现规范 §3.6 未定义 {@code TlTalentAccessGrantVo}（§3.9 的
 * {@code ITalentAccessGrantService.listByTalent} 直接返回实体），故此处
 * {@code BaseMapperPlus} 的 VO 泛型复用实体本身，不新增规范外类型。</p>
 *
 * @author talent
 */
@Mapper
public interface TlTalentAccessGrantMapper extends BaseMapperPlus<TlTalentAccessGrant, TlTalentAccessGrant> {

    /**
     * 查询当前生效的单条授权。
     * <p>命中条件：未删除、状态正常（status='0'）、生效时间已到或为空、失效时间未过或为空，
     * 且 grantee 命中「本人 USER」或「登录用户角色 ROLE（grantee_id 存 sys_role.role_id）」。</p>
     *
     * @param talentId 人才ID
     * @param userId   当前登录用户ID，可为 null
     * @param roleKeys 当前登录用户的角色标识（sys_role.role_key）集合，可为空
     * @return 生效的授权记录列表
     */
    List<TlTalentAccessGrant> selectActiveGrants(@Param("talentId") Long talentId,
                                                 @Param("userId") Long userId,
                                                 @Param("roleKeys") Collection<String> roleKeys);

}
