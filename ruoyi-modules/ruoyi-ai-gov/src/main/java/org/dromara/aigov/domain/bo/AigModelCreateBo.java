package org.dromara.aigov.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.common.core.validate.AddGroup;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 新增模型业务对象。
 *
 * <p>一个对象同时承载两件事：</p>
 * <ol>
 *     <li><b>模型主数据</b>——登记进 snail-ai 的 {@code sai_model_config}；</li>
 *     <li><b>首份治理属性</b>——登记进 {@code aig_model_governance}。</li>
 * </ol>
 *
 * <p><b>为什么治理属性在新增时就必填</b>：路由引擎在「步骤 3/4」会排除「未登记治理属性」
 * 的候选模型。若允许先建模型、治理属性留空，会立刻产生一个看起来存在、却永远不会被
 * 任何策略选中的「孤儿模型」，排查成本很高。故部署类型、数据等级上限、生命周期三项
 * 在新增时即必填。</p>
 *
 * <p><b>密钥</b>：分两条路——</p>
 * <ul>
 *     <li>{@code secretRef}：只登记「引用」（如 {@code kms://ai/qwen}），禁止明文；</li>
 *     <li>{@code apiKey}：<b>明文密钥</b>，由治理层按 snail-ai 的 SM4 口径加密后写入
 *         {@code sai_model_config.api_key}。仅当 {@code aigov.model-crypto.enabled=true}
 *         且持有 {@code aig:model:secret} 权限时接受；本字段<b>只进不出</b>，
 *         任何查询响应都不会回显。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Data
public class AigModelCreateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 新增后回填的自增主键（{@code sai_model_config.id}），仅供服务端内部使用
     */
    private Long id;

    /**
     * 供应商ID（{@code sai_model_provider.id}）
     */
    @NotNull(message = "供应商不能为空", groups = {AddGroup.class})
    private Long providerId;

    /**
     * 模型名称（展示用）
     */
    @NotBlank(message = "模型名称不能为空", groups = {AddGroup.class})
    @Size(max = 255, message = "模型名称长度不能超过 255", groups = {AddGroup.class})
    private String modelName;

    /**
     * 模型标识（业务唯一键，路由与审计按它引用模型）。
     *
     * <p><b>必须允许「斜杠 / 冒号 / 开头的波浪号」</b>：这个字段最终会作为请求体的
     * {@code model} 原样发给上游，而主流聚合网关的模型 ID 就是 {@code vendor/model} 形式，
     * 免费档还带 {@code :free} 后缀，浮动别名以 {@code ~} 开头。
     * 早期规则只允许「字母数字点下划线中划线」，实测对 OpenRouter 公开目录里的
     * 443 个模型 ID <b>一个都接受不了</b>（全部含斜杠）——那等于把这类供应商挡在门外，
     * 也会逼得使用者手改成别的名字，最终表现为上游报「xx is not a valid model ID」。</p>
     *
     * <p>规则取自 {@link AigConstants#MODEL_KEY_PATTERN}，与编辑入口共用一份，避免漂移。
     * 放开这几个字符是安全的：{@code model_key} 只进 JSON 请求体、SQL 参数与审计日志，
     * 从不被拼进 URL 路径或文件名（已全仓核查）。</p>
     */
    @NotBlank(message = "模型标识不能为空", groups = {AddGroup.class})
    @Size(max = 100, message = "模型标识长度不能超过 100", groups = {AddGroup.class})
    @Pattern(regexp = AigConstants.MODEL_KEY_PATTERN, message = AigConstants.MODEL_KEY_PATTERN_MESSAGE,
        groups = {AddGroup.class})
    private String modelKey;

    /**
     * 模型类型（CHAT / EMBEDDING 等）
     */
    @NotBlank(message = "模型类型不能为空", groups = {AddGroup.class})
    @Size(max = 50, message = "模型类型长度不能超过 50", groups = {AddGroup.class})
    private String modelType;

    /**
     * 适配器标识（如 openai-compatible、local-rule）
     */
    @Size(max = 100, message = "适配器标识长度不能超过 100", groups = {AddGroup.class})
    private String adapterKey;

    /**
     * 接口地址；本地部署可留空
     */
    @Size(max = 500, message = "接口地址长度不能超过 500", groups = {AddGroup.class})
    private String apiEndpoint;

    /**
     * 明文 API 密钥，可留空（留空表示稍后再配）。
     *
     * <p>服务端按 snail-ai 的 SM4 口径加密后写入 {@code sai_model_config.api_key}；该列是
     * snail-ai 运行时的凭据来源，故此处填错的直接后果是「模型调用失败」。
     * 写此项需要 {@code aig:model:secret} 权限，且 {@code aigov.model-crypto.enabled=true}。</p>
     *
     * <p>{@code sai_model_config.api_key} 列为 {@code VARCHAR(1000)}；SM4 密文经 Base64 后
     * 会膨胀到约 {@code ceil(n/16)*16*4/3}，故明文上限取 500，为密文留出余量。</p>
     */
    @Size(max = 500, message = "API 密钥长度不能超过 500", groups = {AddGroup.class})
    private String apiKey;

    /**
     * 说明
     */
    @Size(max = 1000, message = "说明长度不能超过 1000", groups = {AddGroup.class})
    private String description;

    /**
     * 作用域（GLOBAL / LOCAL），留空按 GLOBAL
     */
    @Size(max = 20, message = "作用域长度不能超过 20", groups = {AddGroup.class})
    private String scope;

    /**
     * 是否默认模型
     */
    private Boolean isDefault;

    /**
     * 是否启用
     */
    private Boolean isEnabled;

    // ------------------------------------------------------------------
    // 首份治理属性（新增时必填的三项 + 可选补充项）
    // ------------------------------------------------------------------

    /**
     * 部署类型（LOCAL / GROUP / EXTERNAL_ENTERPRISE / EXTERNAL_API）
     */
    @NotBlank(message = "部署类型不能为空", groups = {AddGroup.class})
    @Size(max = 24, message = "部署类型长度不能超过 24", groups = {AddGroup.class})
    private String deploymentType;

    /**
     * 允许处理的最高数据等级
     */
    @NotBlank(message = "最高数据等级不能为空", groups = {AddGroup.class})
    @Size(max = 16, message = "最高数据等级长度不能超过 16", groups = {AddGroup.class})
    private String dataLevelMax;

    /**
     * 生命周期状态（CANDIDATE/TRIAL/GRAY/PRODUCTION/SUSPENDED/RETIRED）
     */
    @NotBlank(message = "生命周期状态不能为空", groups = {AddGroup.class})
    @Size(max = 16, message = "生命周期状态长度不能超过 16", groups = {AddGroup.class})
    private String lifecycleStatus;

    /**
     * 密钥引用（禁止明文，如 kms://ai/qwen）；写此项需要 aig:model:secret 权限
     */
    @Size(max = 255, message = "密钥引用长度不能超过 255", groups = {AddGroup.class})
    private String secretRef;

    /**
     * 成本限制说明
     */
    @Size(max = 255, message = "成本限制长度不能超过 255", groups = {AddGroup.class})
    private String costLimit;

    /**
     * 技术负责人
     */
    @Size(max = 64, message = "技术负责人长度不能超过 64", groups = {AddGroup.class})
    private String ownerTech;

    /**
     * 业务负责人
     */
    @Size(max = 64, message = "业务负责人长度不能超过 64", groups = {AddGroup.class})
    private String ownerBiz;

    /**
     * 安全审批人
     */
    @Size(max = 64, message = "安全审批人长度不能超过 64", groups = {AddGroup.class})
    private String ownerSecurity;

    /**
     * 有效期起
     */
    private LocalDate validFrom;

    /**
     * 有效期止
     */
    private LocalDate validTo;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class})
    private String remark;

}
