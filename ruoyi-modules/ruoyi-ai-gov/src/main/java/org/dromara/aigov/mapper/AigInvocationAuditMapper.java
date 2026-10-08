package org.dromara.aigov.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.aigov.domain.AigInvocationAudit;
import org.dromara.aigov.domain.vo.AigErrorClassCountVo;
import org.dromara.aigov.domain.vo.AigInvocationAuditVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 调用逐次审计 Mapper 接口
 * <p>追加型审计表，无逻辑删除；本接口<b>不提供</b>物理删除相关业务方法。</p>
 *
 * <p>{@link #countByCallerSince} 是<b>人均配额</b>的用量来源（C3）：调用是不是「发生过」，
 * 以这张表为准——它是唯一逐次落库的事实记录。计数刻意统计<b>全部行</b>（含失败）：
 * 失败的那次调用同样占用了调用机会（供应商可能已经跑过、也可能只是超时），
 * 只算成功会让「反复失败重试」绕开配额。</p>
 *
 * <p>{@link #countByAgentVersionSince} / {@link #countFailedByAgentVersionSince} /
 * {@link #listErrorClassCountsSince} 是<b>灰度达标</b>（C3，门槛 CANARY）的证据来源：
 * 按 Agent 版本统计窗口内的调用次数、失败次数与错误分类，对应判据 a+b+c。</p>
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

    /**
     * 统计某个 Agent 版本在窗口内的调用次数（灰度达标判据 (a)）。
     *
     * <p>走 {@code idx_aig_audit_agent_version (agent_version_id, operate_time)}。
     * 注意口径：{@code agent_version_id} 为空的行<b>不会</b>被算进任何版本——
     * 那是「本次调用未绑定到某个 Agent 版本」（直接调能力、不经任务），
     * 没有版本可归属，因此不能记到别人头上。</p>
     *
     * @param agentVersionId Agent 版本ID
     * @param since          灰度窗口起点（进入 CANDIDATE 的时间）
     * @return 调用次数
     */
    @Select("select count(*) from aig_invocation_audit "
        + "where agent_version_id = #{agentVersionId} and operate_time >= #{since}")
    long countByAgentVersionSince(@Param("agentVersionId") Long agentVersionId,
                                  @Param("since") LocalDateTime since);

    /**
     * 统计某个 Agent 版本在窗口内的失败次数（灰度达标判据 (b) 的分子）。
     *
     * <p>{@code result='1'} 是 {@code AigInvokeResultEnum.FAILED} 的入库值；
     * 这里写字面量是因为注解值必须是编译期常量（{@code getCode()} 不是）。</p>
     *
     * @param agentVersionId Agent 版本ID
     * @param since          灰度窗口起点
     * @return 失败次数
     */
    @Select("select count(*) from aig_invocation_audit "
        + "where agent_version_id = #{agentVersionId} and operate_time >= #{since} and result = '1'")
    long countFailedByAgentVersionSince(@Param("agentVersionId") Long agentVersionId,
                                        @Param("since") LocalDateTime since);

    /**
     * 按错误分类统计窗口内的次数（灰度达标判据 (c) 的取数）。
     *
     * <p>只取非空分类：成功调用没有分类。是否算「严重」由调用方按
     * {@code AigCanaryEvidence.SEVERE_CLASS_CODES} 判定，SQL 里不写死口径。</p>
     *
     * @param agentVersionId Agent 版本ID
     * @param since          灰度窗口起点
     * @return 分类 → 次数
     */
    @Select("select error_class, count(*) as error_count from aig_invocation_audit "
        + "where agent_version_id = #{agentVersionId} and operate_time >= #{since} "
        + "and error_class is not null group by error_class")
    List<AigErrorClassCountVo> listErrorClassCountsSince(@Param("agentVersionId") Long agentVersionId,
                                                         @Param("since") LocalDateTime since);

}
