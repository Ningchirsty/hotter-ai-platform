package org.dromara.aigov.studio.helper;

import org.dromara.aigov.studio.domain.bo.AigStudioProposalOperationBo;
import org.dromara.common.core.utils.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话式 Copilot 的模型输出解析（附件 07 增量 6）。
 *
 * <h3>它<b>只做形状</b>，不做白名单与类型校验</h3>
 * <p>路径是否允许、值类型对不对，由 {@link AigStudioProposalPlanner} 判定——那是这条通道唯一的
 * 权威。这里若也判一遍，就会出现两份会漂移的白名单；而漂移的方向恰恰是危险的那种：
 * 解析层放行、规划层拒绝（或反过来）时，人看到的原因与实际卡点不一致。</p>
 *
 * <h3>为什么要容忍代码围栏与前后杂文</h3>
 * <p>模型经常把 JSON 包在 ```json 里，或在前面写一句"好的，我来改"。这些不该被判成"模型不行"——
 * 我们要的是那段 JSON。但<b>找不到 JSON 时必须明确失败</b>，不能"尽力猜一个"：
 * 猜出来的操作会被当成模型的原意写进提案。</p>
 *
 * @author ai-gov
 */
public final class AigStudioCopilotParser {

    /**
     * summary 的落库上限（与 {@code aig_studio_change_proposal.summary} 的列宽一致）
     */
    private static final int SUMMARY_MAX = 200;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private AigStudioCopilotParser() {
    }

    /**
     * 解析结果。
     *
     * @param summary    提案说明（可空）
     * @param operations 操作清单（非空；形状已校验，<b>未</b>做白名单与类型校验）
     */
    public record Parsed(String summary, List<AigStudioProposalOperationBo> operations) {
    }

    /**
     * 解析模型输出。
     *
     * @param rawOutput 模型原文
     * @return 解析结果
     * @throws IllegalArgumentException 拿不到 JSON 对象、没有 operations、operations 为空、或某条操作缺 path
     */
    public static Parsed parse(String rawOutput) {
        if (StringUtils.isBlank(rawOutput)) {
            throw new IllegalArgumentException("模型没有返回任何内容");
        }
        String json = extractJson(rawOutput);
        Object parsed;
        try {
            parsed = MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("模型输出的 JSON 无法解析：" + e.getMessage(), e);
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("模型输出必须是一个 JSON 对象（含 summary 与 operations）");
        }

        Object operationsRaw = map.get("operations");
        if (operationsRaw == null) {
            throw new IllegalArgumentException("模型输出里没有 operations 列表");
        }
        if (!(operationsRaw instanceof List<?> list)) {
            throw new IllegalArgumentException("模型输出的 operations 必须是数组");
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("模型的提案里没有任何改动（它认为无需修改，或要求无法用白名单字段表达）");
        }

        List<AigStudioProposalOperationBo> operations = new ArrayList<>(list.size());
        int index = 0;
        for (Object item : list) {
            index++;
            if (!(item instanceof Map<?, ?> op)) {
                throw new IllegalArgumentException("模型输出的第 " + index + " 条操作不是 JSON 对象");
            }
            Object path = op.get("path");
            if (!(path instanceof String text) || StringUtils.isBlank(text)) {
                throw new IllegalArgumentException("模型输出的第 " + index + " 条操作缺少 path");
            }
            if (!op.containsKey("value")) {
                throw new IllegalArgumentException("模型输出的第 " + index + " 条操作缺少 value（要改成什么）");
            }
            AigStudioProposalOperationBo bo = new AigStudioProposalOperationBo();
            bo.setPath(text.trim());
            bo.setValue(op.get("value"));
            operations.add(bo);
        }

        Object summary = map.get("summary");
        String text = summary == null ? null : StringUtils.substring(String.valueOf(summary), 0, SUMMARY_MAX);
        return new Parsed(StringUtils.isBlank(text) ? null : text, operations);
    }

    /**
     * 从模型原文里取出那段 JSON 对象。
     *
     * @param rawOutput 模型原文
     * @return JSON 文本
     * @throws IllegalArgumentException 找不到 JSON 对象
     */
    static String extractJson(String rawOutput) {
        String text = rawOutput.trim();
        int fence = text.indexOf("```");
        if (fence >= 0) {
            int start = text.indexOf('\n', fence);
            int end = start < 0 ? -1 : text.indexOf("```", start);
            if (start > 0 && end > start) {
                // 围栏内可能带语言标记（如 json），直接取第一个 '{' 之后的内容
                text = text.substring(start + 1, end);
            }
        }
        int openBrace = text.indexOf('{');
        int openBracket = text.indexOf('[');
        if (openBrace < 0 && openBracket < 0) {
            throw new IllegalArgumentException("模型输出里没有找到 JSON 对象（应以 { 开头、} 结尾）");
        }
        // 数组也照取：拿得到但它不是对象时，报"必须是对象"比报"找不到 JSON"更能说明问题
        boolean object = openBrace >= 0 && (openBracket < 0 || openBrace < openBracket);
        int open = object ? openBrace : openBracket;
        char closeChar = object ? '}' : ']';
        int close = text.lastIndexOf(closeChar);
        if (close <= open) {
            throw new IllegalArgumentException("模型输出里没有找到 JSON 对象（应以 { 开头、} 结尾）");
        }
        return text.substring(open, close + 1);
    }

}
