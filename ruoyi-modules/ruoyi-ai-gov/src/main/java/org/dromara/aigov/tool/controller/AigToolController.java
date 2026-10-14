package org.dromara.aigov.tool.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.tool.domain.bo.AigToolDefinitionBo;
import org.dromara.aigov.tool.domain.bo.AigToolDefinitionVersionBo;
import org.dromara.aigov.tool.domain.vo.AigToolDefinitionVersionVo;
import org.dromara.aigov.tool.service.IAigToolRegistryService;
import org.dromara.common.core.domain.R;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工具注册接口（设计 §6.5、§9.2 {@code GET /aigov/tools}）。
 *
 * <p><b>读与写分开授权</b>：{@code aig:tool:list} 用于挑选可绑定工具；
 * {@code aig:tool:manage} 用于登记工具定义与执行契约。登记工具会改变"Agent 能申请什么范围"，
 * 因此不与"看清单"共用权限。</p>
 *
 * <p>清单里的 {@code executable} 由服务端按"执行器是否已注册"给出——界面据此如实显示
 * "已登记 待接入"，而不是让前端从前端的字段去猜（设计 §4.1）。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/tools")
public class AigToolController extends BaseController {

    private final IAigToolRegistryService toolRegistryService;

    /**
     * 可绑定工具清单。
     *
     * @return 工具版本清单（含接入与确认状态）
     */
    @SaCheckPermission(AigConstants.PERM_TOOL_LIST)
    @GetMapping
    public R<List<AigToolDefinitionVersionVo>> list() {
        return R.ok(toolRegistryService.listVersions());
    }

    /**
     * 新建工具定义。
     *
     * @param bo 入参
     * @return 工具ID
     */
    @SaCheckPermission(AigConstants.PERM_TOOL_MANAGE)
    @RepeatSubmit
    @PostMapping("/definitions")
    public R<Long> createDefinition(@RequestBody @Validated AigToolDefinitionBo bo) {
        return R.ok(toolRegistryService.createDefinition(bo, LoginHelper.getUserId()));
    }

    /**
     * 新增工具版本（执行契约）。
     *
     * @param bo 入参
     * @return 版本ID
     */
    @SaCheckPermission(AigConstants.PERM_TOOL_MANAGE)
    @RepeatSubmit
    @PostMapping("/versions")
    public R<Long> addVersion(@RequestBody @Validated AigToolDefinitionVersionBo bo) {
        return R.ok(toolRegistryService.addVersion(bo));
    }

}
