package org.dromara.aigov.service;

import java.util.Map;

/**
 * {@code talent_match} 具体能力服务（阶段1 本地规则实现）。
 * <p>设计依据：人才匹配只允许本地模型/规则处理，<b>禁止外发个人信息</b>；
 * 本服务不访问任何网络，只在入参载荷内做规则匹配。</p>
 *
 * @author ai-gov
 */
public interface IAigTalentMatchService {

    /**
     * 执行本地规则匹配，产出结构化 JSON。
     * <p>输入字段：{@code skillTags}（字符串数组或逗号分隔字符串）、
     * {@code candidates}（对象数组，元素含 {@code candidateId}/{@code skills}）。</p>
     * <p>输出结构对齐能力模板 {@code output_schema}：{@code candidates}/{@code score}/
     * {@code evidence}/{@code pendingConfirm}。</p>
     *
     * @param payload 业务载荷
     * @return 结构化 JSON 字符串
     */
    String match(Map<String, Object> payload);

}
