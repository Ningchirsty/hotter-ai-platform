package org.dromara.aigov.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.aigov.domain.AigInvocationAudit;
import org.dromara.aigov.domain.vo.AigInvocationAuditVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.time.LocalDateTime;

/**
 * AI 调用逐次审计 Mapper 接口
 * <p>追加型审计表，无逻辑删除；本接口<b>不提供</b>物理删除相关业务方法。</p>
 *
 * <p>{@link #countByCallerSince} 是<b>人均配额</b>的用量来源（C3）：调用是不是「发生过」，
 * 以这张表为准——它是唯一逐次落库的事实记录。计数刻意统计<b>全部行</b>（含失败）：
 * 失败的那次调用同样占用了调用机会（供应商可能已经跑过、也可能只是超时），
 * 只算成功会让「反复失败重试」绕开配额。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigInvocationAuditMapper extends BaseMapperPlus<AigInvocationAudit, AigInvocationAuditVo> {

    /**
     * 统计某个调用人在给定时间点之后的调用次数（人均配额的用量）。
     *
     * <p>走既有索引 {@code idx_aig_audit_caller (caller_id, operate_time)}，不需要新增索引。</p>
     *
     * @param callerId 调用人用户ID
     * @param since    周期起点（自然日/自然月的 00:00:00）
     * @return 调用次数
     */
    @Select("select count(*) from aig_invocation_audit "
        + "where caller_id = #{callerId} and operate_time >= #{since}")
    long countByCallerSince(@Param("callerId") Long callerId, @Param("since") LocalDateTime since);

}
