package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentGroupMember;
import org.dromara.hrtalent.domain.vo.talent.TalentGroupMemberVo;

import java.time.LocalDateTime;

/**
 * 人才分组成员 Mapper 接口（SPEC-P4 §2.3 C 线）。
 *
 * <p>{@code group_id + talent_id} 由数据库唯一索引 {@code uk_hr_talent_group_member} 兜底；
 * 由于逻辑删除行仍占用唯一索引，重新加入时先<b>恢复</b>历史行
 * （{@link #restoreMember}），保证「重复加入按幂等处理」。</p>
 *
 * <p><b>移出分组只结束关系</b>：不删除人才主档与任何关联资料（设计文档 §8.15）。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentGroupMemberMapper extends BaseMapperPlus<TalentGroupMember, TalentGroupMemberVo> {

    /**
     * 恢复一条已逻辑删除的分组成员关系（幂等重复加入时使用）。
     *
     * <p>只把 {@code del_flag} 复位为 {@code 0} 并刷新加入人/加入时间，
     * 不新增行，避免唯一索引冲突。</p>
     *
     * @param groupId   分组ID
     * @param talentId  人才主档ID
     * @param addedBy   加入人用户ID，可为 null
     * @param addedTime 加入时间
     * @return 受影响行数
     */
    @Update("""
        UPDATE hr_talent_group_member
           SET del_flag = '0',
               added_by = #{addedBy},
               added_time = #{addedTime},
               update_by = #{addedBy},
               update_time = #{addedTime}
         WHERE group_id = #{groupId}
           AND talent_id = #{talentId}
           AND del_flag = '1'
        """)
    int restoreMember(@Param("groupId") Long groupId,
                      @Param("talentId") Long talentId,
                      @Param("addedBy") Long addedBy,
                      @Param("addedTime") LocalDateTime addedTime);

}
