package org.dromara.talent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.talent.domain.TlExportTask;
import org.dromara.talent.domain.vo.TlExportTaskVo;

/**
 * 人才台账导出任务 Mapper 接口
 *
 * @author talent
 */
@Mapper
public interface TlExportTaskMapper extends BaseMapperPlus<TlExportTask, TlExportTaskVo> {

}
