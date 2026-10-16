package org.dromara.aigov.changerequest.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.changerequest.domain.AigChangeRequest;
import org.dromara.aigov.changerequest.domain.vo.AigChangeRequestVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 变更单 Mapper。
 *
 * <p>⚠️ 第二个泛型参数必须是 <b>VO</b>：{@code selectVoPage} 的返回值由它决定，
 * 写成实体会在编译期报"推断的类型不具有兼容的上界"。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigChangeRequestMapper
    extends BaseMapperPlus<AigChangeRequest, AigChangeRequestVo> {
}
