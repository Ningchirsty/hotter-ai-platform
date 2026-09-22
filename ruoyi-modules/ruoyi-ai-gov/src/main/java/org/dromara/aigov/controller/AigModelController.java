package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.domain.bo.AigModelBaseBo;
import org.dromara.aigov.domain.bo.AigModelCreateBo;
import org.dromara.aigov.domain.bo.AigModelGovernanceBo;
import org.dromara.aigov.domain.bo.AigModelProviderBo;
import org.dromara.aigov.domain.bo.AigModelSecretBo;
import org.dromara.aigov.domain.vo.AigModelProviderVo;
import org.dromara.aigov.domain.vo.AigModelTestVo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.service.IAigModelGovernanceService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 模型治理 控制层
 * <p>模型主数据存放在 snail-ai 的 {@code sai_model_config}；本层多数操作只补治理属性，
 * 仅 {@link #createModel} 会写入该表（见 Service 的边界说明）。</p>
 * <p>{@code apiEndpoint} / {@code secretRef} 由 Service 按 {@code aig:model:secret} 权限脱敏，
 * 前端列控制不构成保障，直接调接口同样看不到明文。</p>
 * <p><b>模型密钥</b>（{@code sai_model_config.api_key}）<b>只写不读</b>：
 * {@link #updateModelSecret} 接收明文并加密落库；任何查询接口都不回显密钥，
 * 只回 {@code keyConfigured} 布尔位。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/model")
public class AigModelController {

    private final IAigModelGovernanceService modelGovernanceService;

    /**
     * 分页查询模型清单（sai_model_config 左连治理属性）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 模型分页结果
     */
    @SaCheckPermission(AigConstants.PERM_MODEL_LIST)
    @GetMapping("/list")
    public R<PageResult<AigModelVo>> list(@Validated({Default.class, QueryGroup.class}) AigModelGovernanceBo bo,
                                          PageQuery pageQuery) {
        return R.ok(modelGovernanceService.list(bo, pageQuery));
    }

    /**
     * 供应商下拉选项（新增模型表单用，仅启用的供应商，不含任何凭据）。
     * <p>本映射必须先于 {@code /{modelId}} 被匹配到；Spring 对字面量路径的优先级
     * 高于路径变量（PathPattern 更具体者优先），故二者不会冲突。</p>
     *
     * @return 供应商选项
     */
    @SaCheckPermission(AigConstants.PERM_MODEL_LIST)
    @GetMapping("/providers")
    public R<List<AigModelProviderVo>> providers() {
        return R.ok(modelGovernanceService.listProviders());
    }

    /**
     * 供应商管理列表（含停用项与各供应商下模型数量）。
     * <p>与 {@code /providers} 的区别：那个是给新增模型表单用的「仅启用」下拉，
     * 这个是给供应商管理用的全量列表，因此单独一个路径，避免下拉选项被停用项污染。</p>
     *
     * @return 供应商列表
     */
    @SaCheckPermission(AigConstants.PERM_MODEL_LIST)
    @GetMapping("/providers/all")
    public R<List<AigModelProviderVo>> allProviders() {
        return R.ok(modelGovernanceService.listAllProviders());
    }

    /**
     * 新增供应商：内置 7 家之外，接入自建推理服务/内部网关/新云厂商时先建供应商。
     * <p>权限沿用「新增模型」所需的写权限（{@code aig:model:add}）：建供应商本身就是为了登记模型，
     * 单独再切一个权限点只会让授权更碎；表里也没有任何密钥列，不引入新的敏感面。</p>
     *
     * @param bo 供应商参数
     * @return 新供应商ID
     */
    @SaCheckPermission(AigConstants.PERM_MODEL_ADD)
    @RepeatSubmit
    @PostMapping("/provider")
    public R<Long> createProvider(@Validated @RequestBody AigModelProviderBo bo) {
        return R.ok(modelGovernanceService.createProvider(bo));
    }

    /**
     * 修改供应商：只允许名称/说明/图标/启停，标识不可改。
     *
     * @param bo 供应商参数（id 必填）
     * @return 影响行数
     */
    @SaCheckPermission(AigConstants.PERM_MODEL_ADD)
    @RepeatSubmit
    @PutMapping("/provider")
    public R<Integer> updateProvider(@Validated @RequestBody AigModelProviderBo bo) {
        return R.ok(modelGovernanceService.updateProvider(bo));
    }

    /**
     * 模型连通性测试：按部署类型/适配器分流探测，并把健康状态写回治理表。
     * <p>权限用 {@code aig:model:edit}（治理写权限）而不是只读权限：这个接口会带着平台侧密钥
     * 真的向外发一次请求，只读账号不应具备触发外呼的能力。</p>
     *
     * @param modelId 模型ID
     * @return 测试结果（不含密钥）
     */
    @SaCheckPermission(AigConstants.PERM_MODEL_EDIT)
    @PostMapping("/{modelId}/test")
    public R<AigModelTestVo> testConnection(@NotNull(message = "主键不能为空")
                                            @PathVariable("modelId") Long modelId) {
        return R.ok(modelGovernanceService.testConnection(modelId));
    }

    /**
     * 获取单个模型及其治理属性。
     *
     * @param modelId 模型ID（sai_model_config.id）
     * @return 模型详情
     */
    @SaCheckPermission(AigConstants.PERM_MODEL_QUERY)
    @GetMapping("/{modelId}")
    public R<AigModelVo> getInfo(@NotNull(message = "主键不能为空")
                                 @PathVariable("modelId") Long modelId) {
        return R.ok(modelGovernanceService.getDetail(modelId));
    }

    /**
     * 新增模型：登记模型主数据并同时写入首份治理属性。
     *
     * <p>治理层写入 {@code sai_model_config} 的入口之一，属阶段1 的有意调整
     * （原设计只读该表，但 snail-ai 服务端未就绪时无处登记模型）。写侧约束：
     * 只 INSERT、列白名单。</p>
     *
     * <p>{@code apiKey} 是<b>可选明文</b>密钥，由 Service 加密后写入
     * {@code sai_model_config.api_key}；非空时额外要求 {@code aig:model:secret}。
     * 操作日志通过 {@code excludeParamNames} 剔除该字段，避免明文进 {@code sys_oper_log}。</p>
     *
     * @param bo 新增模型参数
     * @return 新模型ID
     */
    @SaCheckPermission(AigConstants.PERM_MODEL_ADD)
    @Log(title = "AI模型治理", businessType = BusinessType.INSERT, excludeParamNames = {"apiKey"})
    @RepeatSubmit
    @PostMapping
    public R<Long> createModel(@Validated({Default.class, AddGroup.class}) @RequestBody AigModelCreateBo bo) {
        return R.ok(modelGovernanceService.createModel(bo));
    }

    /**
     * 写入/清除模型密钥。
     *
     * <p><b>为什么单独一个接口</b>：密钥比治理属性敏感得多，拆成独立接口才能让
     * {@code aig:model:secret} 单独把关——若并入 {@code /governance}，
     * 「改治理属性」的权限会顺带升级成「改全部模型凭据」。</p>
     *
     * <p><b>明文只进不出</b>：请求体里的 {@code apiKey} 由 Service 加密后写入库，
     * 任何查询接口都不会回显；操作日志同样剔除该字段。</p>
     *
     * @param bo 密钥参数（{@code clearKey=true} 时清除已有密钥）
     * @return 影响行数
     */
    @SaCheckPermission(AigConstants.PERM_MODEL_SECRET)
    @Log(title = "AI模型密钥", businessType = BusinessType.UPDATE, excludeParamNames = {"apiKey"})
    @RepeatSubmit
    @PutMapping("/secret")
    public R<Integer> updateModelSecret(@Validated({Default.class, EditGroup.class}) @RequestBody AigModelSecretBo bo) {
        return R.ok(modelGovernanceService.updateModelSecret(bo));
    }

    /**
     * 编辑模型主数据（{@code sai_model_config} 的白名单列，<b>不含 api_key</b>）。
     *
     * <p><b>为什么需要它</b>：原先治理层只有「新增」一条写路径，登记错了既改不了
     * （界面上「模型键」是只读展示）也删不掉（无 DELETE 入口），只能跳到 snail-ai 管理端。
     * 实测就有人因旧校验不允许斜杠、把 {@code openrouter/free} 写成了 {@code orfree}，
     * 之后无处可改。下架仍不经过治理层（{@code DELETE /aigov/model} 明确不提供）。</p>
     *
     * <p>密钥不在这里改：{@code apiKey} 有独立入口 {@code PUT /aigov/model/secret}，
     * 权限要求也更严（{@code aig:model:secret}）。</p>
     *
     * @param bo 编辑参数
     * @return 影响行数
     */
    @SaCheckPermission(AigConstants.PERM_MODEL_EDIT)
    @Log(title = "AI模型主数据", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/base")
    public R<Integer> updateModelBase(@Validated({Default.class, EditGroup.class}) @RequestBody AigModelBaseBo bo) {
        return R.ok(modelGovernanceService.updateModelBase(bo));
    }

    /**
     * 登记/更新模型治理属性（不存在则新建治理记录）。
     * <p>更新口径：{@code null} 字段不参与更新（与 MyBatis-Plus 的 NOT_NULL 策略一致），
     * 因此无 {@code aig:model:secret} 权限的前端不下发 {@code secretRef} 也不会清空已有引用。</p>
     *
     * @param bo 治理参数
     * @return 治理记录ID
     */
    @SaCheckPermission(AigConstants.PERM_MODEL_EDIT)
    @RepeatSubmit
    @PutMapping("/governance")
    public R<Long> saveGovernance(@Validated({Default.class, EditGroup.class}) @RequestBody AigModelGovernanceBo bo) {
        return R.ok(modelGovernanceService.saveGovernance(bo));
    }

}
