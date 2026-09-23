package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitImportBatch;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitImportBatchVo;

/**
 * 招聘数据导入批次 Mapper。
 *
 * @author hr-talent
 */
@Mapper
public interface RecruitImportBatchMapper extends BaseMapperPlus<RecruitImportBatch, RecruitImportBatchVo> {

}
