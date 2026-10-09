package org.dromara.aigov.task.service;

import org.dromara.aigov.task.domain.bo.AigTaskArtifactBo;
import org.dromara.aigov.task.domain.vo.AigTaskArtifactVo;

import java.util.List;

/**
 * 任务制品账本服务（V2 执行契约的「输出制品」）。
 *
 * <p><b>它解决的问题</b>：在它之前，「这次任务产出了什么文件」只存在于生产方自己的日志与
 * 业务域的资产表里。平台侧的执行结果只有一段文本/JSON，于是
 * 「这次调用到底交付了什么、多大、是不是同一份」没人能回答——
 * 而 {@code execution-result} 契约里的 {@code outputs[]} 与事件
 * {@code AI_TASK_ARTIFACT_ADDED} 正是为回答这个问题而定的。</p>
 *
 * <p><b>口径</b>：</p>
 * <ol>
 *     <li>制品的字节在对象存储里，账本只记引用（{@code storageRef}）与摘要；
 *         {@code artifactId} 是平台铸造的、供事件与执行结果引用的稳定ID；</li>
 *     <li>不合格的登记<b>不是</b>静默丢弃：落一条 {@code FAIL} 证据行（独立事务，
 *         不会被调用方回滚带走），同时向调用方抛错（错误码 {@code ARTIFACT_INVALID}）；</li>
 *     <li>{@code sha256} 是生产方声明值，v1 平台不回读重算，因此账本里
 *         {@code hashVerified=N} 必须与它一起展示——「有哈希」不等于「哈希被验过」。</li>
 * </ol>
 *
 * @author ai-gov
 */
public interface IAigTaskArtifactService {

    /**
     * 登记一份制品。
     *
     * <p>同一任务内「同一对象引用 + 同一哈希」重复登记<b>幂等</b>：返回既有制品ID，
     * 不产生第二行（生产方重推是常态，重复计数会让「这次产出了几份」变成错的）。</p>
     *
     * @param bo 登记入参
     * @return 入库的制品视图
     * @throws org.dromara.common.core.exception.ServiceException 校验不通过（已留 FAIL 证据）或任务不存在
     */
    AigTaskArtifactVo register(AigTaskArtifactBo bo);

    /**
     * 查某任务的制品。
     *
     * @param taskId          任务ID
     * @param includeRejected 是否包含被拒记录（默认只给通过行；排查时带上）
     * @return 制品清单（新→旧）
     */
    List<AigTaskArtifactVo> listByTask(Long taskId, boolean includeRejected);

}
