package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigPackageInstallLog;
import org.dromara.aigov.agent.domain.vo.AigPackageInstallLogVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * Package 安装日志 Mapper（追加型账本：只 insert 与查询，不更新、不删除）。
 *
 * @author ai-gov
 */
@Mapper
public interface AigPackageInstallLogMapper
    extends BaseMapperPlus<AigPackageInstallLog, AigPackageInstallLogVo> {
}
