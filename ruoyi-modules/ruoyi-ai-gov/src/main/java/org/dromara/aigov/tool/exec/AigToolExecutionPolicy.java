package org.dromara.aigov.tool.exec;

import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.aigov.tool.enums.AigToolOperationTypeEnum;
import org.dromara.common.core.utils.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工具执行的前置判据（设计 §7.5）。
 *
 * <p>纯函数、可逐条单测：它回答三个"错了也不报错"的问题——</p>
 * <ol>
 *     <li><b>这个工具现在能不能执行</b>：没有已注册执行器的一律不能（"已登记 待接入"）；</li>
 *     <li><b>要不要人工确认</b>：变更业务数据 / 外部发送必须确认；</li>
 *     <li><b>批准还有效吗</b>：批准绑定输入摘要，参数一变摘要就变、原批准失效；过期同样失效。</li>
 * </ol>
 *
 * @author ai-gov
 */
public final class AigToolExecutionPolicy {

    private AigToolExecutionPolicy() {
    }

    /**
     * 执行判据结论。
     *
     * @param allowed              是否可直接执行
     * @param confirmationRequired 是否等待人工确认（尚未执行，不是失败）
     * @param reason               说明（allowed 时为空）
     */
    public record Decision(boolean allowed, boolean confirmationRequired, String reason) {

        static Decision permit() {
            return new Decision(true, false, null);
        }

        static Decision awaitConfirmation(String reason) {
            return new Decision(false, true, reason);
        }

        static Decision deny(String reason) {
            return new Decision(false, false, reason);
        }
    }

    /**
     * 判断一次调用可否执行。
     *
     * @param operationType     工具版本声明的操作类型
     * @param executorAvailable 是否有已注册的执行器
     * @return 结论
     */
    public static Decision decide(String operationType, boolean executorAvailable) {
        if (!executorAvailable) {
            return Decision.deny("该工具已登记、待接入：当前没有已注册的执行器，不能执行");
        }
        AigToolOperationTypeEnum op = AigToolOperationTypeEnum.find(operationType);
        if (op == null) {
            return Decision.deny("工具操作类型不可识别：" + operationType);
        }
        if (op.isRequiresConfirmation()) {
            return Decision.awaitConfirmation("该工具属于「" + op.getDesc()
                + "」，必须先取得人工确认；且确认绑定输入摘要，参数变化会使原批准失效");
        }
        return Decision.permit();
    }

    /**
     * 工具是否命中"有效绑定"（设计 §7.5：「工具许可既要命中版本声明，也要命中有效绑定」）。
     *
     * <p>绑定只做<b>收窄</b>：没有任何 TOOL 绑定时不额外收窄（返回 true）；一旦有绑定，
     * 请求的工具编码必须在其中——否则就是"绕过绑定直接用版本声明之外的工具"。</p>
     *
     * @param toolCode       请求的工具编码
     * @param boundToolCodes 该版本启用的 TOOL 绑定编码（可空/空=不额外收窄）
     * @return 允许返回 true
     */
    public static boolean bindingsPermit(String toolCode, List<String> boundToolCodes) {
        if (boundToolCodes == null || boundToolCodes.isEmpty()) {
            return true;
        }
        if (StringUtils.isBlank(toolCode)) {
            return false;
        }
        String wanted = toolCode.trim();
        for (String bound : boundToolCodes) {
            if (bound != null && wanted.equalsIgnoreCase(bound.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算输入摘要（人工确认绑定它）。
     *
     * @param toolCode 工具编码
     * @param version  版本
     * @param inputJson 输入
     * @return 摘要
     */
    public static String inputDigest(String toolCode, String version, String inputJson) {
        return DigestUtil.sha256Hex(StringUtils.blankToDefault(toolCode, "") + "|"
            + StringUtils.blankToDefault(version, "") + "|" + StringUtils.blankToDefault(inputJson, ""));
    }

    /**
     * 批准是否仍然有效：摘要必须一致且未过期。
     *
     * <p>截止时刻本身仍有效（闭区间口径）。</p>
     *
     * @param expectedDigest 本次输入摘要
     * @param approvalDigest 批准绑定的摘要
     * @param expiresAt      批准有效期（可空=不设）
     * @param now            判定时刻（可空=当前）
     * @return 有效返回 true
     */
    public static boolean approvalValid(String expectedDigest, String approvalDigest,
                                        LocalDateTime expiresAt, LocalDateTime now) {
        if (StringUtils.isBlank(expectedDigest) || StringUtils.isBlank(approvalDigest)) {
            return false;
        }
        if (!expectedDigest.equalsIgnoreCase(approvalDigest.trim())) {
            // 参数变了 → 摘要不同 → 原批准失效（设计 §7.5）
            return false;
        }
        if (expiresAt == null) {
            return true;
        }
        LocalDateTime time = now == null ? LocalDateTime.now() : now;
        return !time.isAfter(expiresAt);
    }

}
