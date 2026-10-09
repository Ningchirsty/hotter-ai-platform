package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigSandboxRun;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 沙箱运行证据 Mapper。
 *
 * <p>与 {@code AigSandboxRun} 一样 T == V（没有独立 VO）：账本只被写入与只读查询，
 * 字段就是它要表达的全部。查询直接由证据服务用 LambdaQueryWrapper 表达
 * （取该版本最近一次运行），不需要自定义 SQL。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigSandboxRunMapper extends BaseMapperPlus<AigSandboxRun, AigSandboxRun> {

}
