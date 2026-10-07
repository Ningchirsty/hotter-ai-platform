package org.dromara.aigov.task.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.task.domain.AigTaskSnapshot;
import org.dromara.aigov.task.domain.vo.AigTaskSnapshotVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 不可变输入快照 Mapper。
 *
 * <p>只应当被 insert 与按任务查询使用。<b>没有 update/delete 的业务入口</b>：
 * 快照冻结后不可变，改内容必须新增版本（{@code snapshot_version} 递增）。
 * 本接口继承的方法里带 update/delete，属于 MyBatis-Plus 的通用能力，
 * 服务层不暴露它们。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigTaskSnapshotMapper extends BaseMapperPlus<AigTaskSnapshot, AigTaskSnapshotVo> {

}
