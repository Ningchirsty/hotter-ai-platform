package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.domain.bo.AigModelGovernanceBo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.service.IAigModelGovernanceService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 模型治理 控制层
 * <p>模型主数据来自 snail-ai 的 {@code sai_model_config}（只读）；
 * {@code apiEndpoint} / {@code secretRef} 由 Service 按 {@code aig:model:secret} 权限脱敏，
 * 前端列控制不构成保障，直接调接口同样看不到明文。</p>
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
