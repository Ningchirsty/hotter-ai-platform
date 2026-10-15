package org.dromara.aigov.execution;

import java.io.Serializable;

/**
 * 执行计划里冻结的一条知识引用（设计 §7.4：sourceId、documentVersionId、page、section、chunkId、excerptRef）。
 *
 * <p>它是"这次运行依据了哪些资料"的持久证据：只记下被采用的那几条，而不是整份检索结果。
 * 引用 ID 全部来自服务端合法集合（{@code KnowledgePort} 的授权过滤之后），
 * 因此可以放心地作为可点击来源下发给页面。</p>
 *
 * @author ai-gov
 */
public record AigExecutionCitation(Long chunkId, Long versionId, Long documentId,
                                   String sourceName, String locator, String excerptRef,
                                   String dataLevel) implements Serializable {
}
