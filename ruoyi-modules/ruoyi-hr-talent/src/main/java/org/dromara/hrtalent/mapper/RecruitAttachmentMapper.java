package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitAttachment;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitAttachmentVo;

/**
 * 招聘业务附件 Mapper。
 *
 * <p>通用 CRUD 走 {@link BaseMapperPlus}；额外的版本号查询用于保证同一业务对象同一附件类型的
 * {@code version_no} <b>单调递增</b>：查询刻意<b>不</b>过滤 {@code del_flag}，
 * 避免逻辑删除旧版本后新版本号被重复使用（旧行仍在库中，版本号不应复用）。</p>
 *
 * @author hr-talent
 */
public interface RecruitAttachmentMapper extends BaseMapperPlus<RecruitAttachment, RecruitAttachmentVo> {

    /**
     * 查询同一业务对象同一附件类型已使用的最大版本号（含逻辑删除记录）。
     *
     * @param bizType  业务类型
     * @param bizId    业务对象ID
     * @param fileType 附件类型
     * @return 已使用的最大版本号；从未上传过返回 0
     */
    @Select("""
        select coalesce(max(version_no), 0)
        from hr_recruit_attachment
        where biz_type = #{bizType}
          and biz_id = #{bizId}
          and file_type = #{fileType}
        """)
    Integer selectMaxVersionNo(@Param("bizType") String bizType,
                               @Param("bizId") Long bizId,
                               @Param("fileType") String fileType);

}
