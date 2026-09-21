package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentProfileChange;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileChangeVo;

/**
 * 人才关键字段变更历史 Mapper 接口（设计文档 §9.2）。
 *
 * <p>只追加、不覆盖、不物理删除；列表查询走
 * {@link TalentProfileMapper#selectChangePage} 以复用人才可见范围条件。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentProfileChangeMapper extends BaseMapperPlus<TalentProfileChange, TalentProfileChangeVo> {

}
