package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigPackageRejection;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * Package 注册被拒证据 Mapper。
 *
 * <p>与 {@code AigPackageRejection} 一样，T == V（没有独立 VO）：
 * 这个账本只被写入与只读查询，字段就是它要表达的全部，
 * 再包一层视图只会多一处需要同步的地方。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigPackageRejectionMapper
    extends BaseMapperPlus<AigPackageRejection, AigPackageRejection> {

}
