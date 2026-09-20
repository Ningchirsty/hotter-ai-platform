package org.dromara.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.CpFactSnapshot;
import org.dromara.content.domain.vo.CpFactSnapshotVo;

/**
 * 产品事实快照 Mapper 接口
 *
 * @author content
 */
@Mapper
public interface CpFactSnapshotMapper extends BaseMapperPlus<CpFactSnapshot, CpFactSnapshotVo> {

}
