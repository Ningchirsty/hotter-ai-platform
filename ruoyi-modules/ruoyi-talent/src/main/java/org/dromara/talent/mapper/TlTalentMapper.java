package org.dromara.talent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.talent.domain.TlTalent;
import org.dromara.talent.domain.vo.TlTalentVo;

import java.util.List;

/**
 * 人才主档 Mapper 接口
 * <p>按架构决策，本模块所有 Mapper 均<b>不使用</b> {@code @DataPermission}，
 * 区域数据范围由 TalentScopeHelper 在服务端显式追加条件。</p>
 *
 * @author talent
 */
@Mapper
public interface TlTalentMapper extends BaseMapperPlus<TlTalent, TlTalentVo> {

    /**
     * 重复预检：按 phone_hash 精确命中（排除自身与已删除记录）
     *
     * @param phoneHash       手机号标准化哈希
     * @param excludeTalentId 需要排除的人才ID，可为 null
     * @return 命中的人才列表
     */
    List<TlTalent> selectByPhoneHash(@Param("phoneHash") String phoneHash,
                                     @Param("excludeTalentId") Long excludeTalentId);

    /**
     * 弱匹配：姓名 + 手机号后四位（排除自身与已删除记录）
     *
     * @param name            姓名
     * @param phoneTail4      手机号后四位
     * @param excludeTalentId 需要排除的人才ID，可为 null
     * @return 命中的人才列表
     */
    List<TlTalent> selectByNameAndTail4(@Param("name") String name,
                                        @Param("phoneTail4") String phoneTail4,
                                        @Param("excludeTalentId") Long excludeTalentId);

    /**
     * 生成人才编号用：取指定日期前缀下的最大编号
     *
     * @param datePrefix 日期前缀，如 TL20250101
     * @return 最大人才编号，无数据时返回 null
     */
    String selectMaxTalentNo(@Param("datePrefix") String datePrefix);

}
