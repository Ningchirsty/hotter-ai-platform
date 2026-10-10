package org.dromara.aigov.workspace.launch.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.workspace.launch.domain.AigLaunchRecord;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 启动记录 Mapper。
 *
 * <p>泛型第二参数是实体自己（T == V）的理由见 {@code AigScenarioMapper}：
 * 本增量只需要数据访问，不为 VO 好看而多加一层转换器。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigLaunchRecordMapper extends BaseMapperPlus<AigLaunchRecord, AigLaunchRecord> {

}
