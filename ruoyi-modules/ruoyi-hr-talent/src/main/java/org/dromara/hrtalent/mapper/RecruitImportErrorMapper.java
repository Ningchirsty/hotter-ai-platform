package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitImportError;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitImportErrorVo;

/**
 * 招聘数据导入错误 Mapper。
 *
 * @author hr-talent
 */
@Mapper
public interface RecruitImportErrorMapper extends BaseMapperPlus<RecruitImportError, RecruitImportErrorVo> {

}
