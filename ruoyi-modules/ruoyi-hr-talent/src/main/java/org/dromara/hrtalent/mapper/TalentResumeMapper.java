package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentResume;
import org.dromara.hrtalent.domain.vo.talent.TalentResumeVo;

/**
 * 人才简历版本 Mapper（SPEC-P4 §2.1）。
 *
 * <p><b>授权约定</b>：本 Mapper <b>不</b>实现任何授权规则；调用方必须先经
 * {@code TalentScopeDomainService}（经人才主档服务）完成资源级鉴权，
 * 再用 {@code talent_id} 做主子过滤（设计文档 §11.1）。</p>
 *
 * @author hr-talent
 */
public interface TalentResumeMapper extends BaseMapperPlus<TalentResume, TalentResumeVo> {

    /**
     * 查询同一人才已使用的最大版本号。
     *
     * <p>刻意<b>不</b>过滤 {@code del_flag}：逻辑删除的旧版本仍占用版本号，
     * 若过滤会导致新版本号被复用，破坏 {@code version_no} 单调递增语义
     * （与 {@code RecruitAttachmentMapper#selectMaxVersionNo} 同口径）。</p>
     *
     * @param talentId 人才主档ID
     * @return 已使用的最大版本号；从未上传过返回 0
     */
    @Select("""
        select coalesce(max(version_no), 0)
        from hr_talent_resume
        where talent_id = #{talentId}
        """)
    Integer selectMaxVersionNo(@Param("talentId") Long talentId);

}
