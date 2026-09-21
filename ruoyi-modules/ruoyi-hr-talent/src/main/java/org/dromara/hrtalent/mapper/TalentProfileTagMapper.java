package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentProfileTag;

import java.time.LocalDateTime;

/**
 * 人才标签关系 Mapper 接口（SPEC-P4 §2.3 C 线）。
 *
 * <p>{@code talent_id + tag_id} 由数据库唯一索引 {@code uk_hr_talent_profile_tag} 兜底；
 * 由于逻辑删除行仍占用唯一索引，重新挂载同一标签时必须先<b>恢复</b>历史行
 * （{@link #restoreRelation}），否则会撞唯一键。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentProfileTagMapper extends BaseMapperPlus<TalentProfileTag, TalentProfileTag> {

    /**
     * 恢复一条已逻辑删除的标签关系（幂等重挂同一标签时使用）。
     *
     * <p>只把 {@code del_flag} 复位为 {@code 0} 并刷新确认人/确认时间，
     * 不新增行，避免唯一索引冲突。</p>
     *
     * @param talentId      人才主档ID
     * @param tagId         标签ID
     * @param sourceType    来源类型（manual手工/resume解析/system系统）
     * @param confirmedBy   确认人用户ID，可为 null
     * @param confirmedTime 确认时间
     * @return 受影响行数
     */
    @Update("""
        UPDATE hr_talent_profile_tag
           SET del_flag = '0',
               source_type = #{sourceType},
               confirmed_by = #{confirmedBy},
               confirmed_time = #{confirmedTime},
               update_by = #{confirmedBy},
               update_time = #{confirmedTime}
         WHERE talent_id = #{talentId}
           AND tag_id = #{tagId}
           AND del_flag = '1'
        """)
    int restoreRelation(@Param("talentId") Long talentId,
                        @Param("tagId") Long tagId,
                        @Param("sourceType") String sourceType,
                        @Param("confirmedBy") Long confirmedBy,
                        @Param("confirmedTime") LocalDateTime confirmedTime);

}
