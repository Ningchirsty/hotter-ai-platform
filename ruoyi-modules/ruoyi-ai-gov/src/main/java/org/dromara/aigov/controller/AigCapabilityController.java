package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.domain.bo.AigCapabilityBo;
import org.dromara.aigov.domain.vo.AigCapabilityVo;
import org.dromara.aigov.service.IAigCapabilityService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 能力目录 控制层
 * <p>本层只做参数接收与组装 {@link R}；授权、数据范围与审计一律由 Service 负责。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/capability")
public class AigCapabilityController {

    private final IAigCapabilityService capabilityService;

    /**
     * 分页查询能力目录。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 能力分页结果
     */
    @SaCheckPermission(AigConstants.PERM_CAPABILITY_LIST)
    @GetMapping("/list")
    public R<PageResult<AigCapabilityVo>> list(@Validated({Default.class, QueryGroup.class}) AigCapabilityBo bo,
                                               PageQuery pageQuery) {
        return R.ok(capabilityService.queryPage(bo, pageQuery));
    }

    /**
     * 获取能力详情。
     *
     * @param capabilityId 能力ID
     * @return 能力详情
     */
    @SaCheckPermission(AigConstants.PERM_CAPABILITY_QUERY)
    @GetMapping("/{capabilityId}")
    public R<AigCapabilityVo> getInfo(@NotNull(message = "主键不能为空")
                                      @PathVariable("capabilityId") Long capabilityId) {
        return R.ok(capabilityService.getDetail(capabilityId));
    }

    /**
     * 新增能力。
     *
     * @param bo 能力参数
     * @return 新增的能力ID
     */
    @SaCheckPermission(AigConstants.PERM_CAPABILITY_ADD)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody AigCapabilityBo bo) {
        return R.ok(capabilityService.create(bo));
    }

    /**
     * 修改能力。
     *
     * @param bo 能力参数
     * @return 操作结果
     */
    @SaCheckPermission(AigConstants.PERM_CAPABILITY_EDIT)
    @RepeatSubmit
    @PutMapping
    public R<Void> edit(@Validated({Default.class, EditGroup.class}) @RequestBody AigCapabilityBo bo) {
        capabilityService.update(bo);
        return R.ok();
    }

    /**
     * 删除能力（逻辑删除）。
     *
     * @param capabilityId 能力ID
     * @return 操作结果
     */
    @SaCheckPermission(AigConstants.PERM_CAPABILITY_REMOVE)
    @RepeatSubmit
    @DeleteMapping("/{capabilityId}")
    public R<Void> remove(@NotNull(message = "主键不能为空")
                          @PathVariable("capabilityId") Long capabilityId) {
        capabilityService.remove(capabilityId);
        return R.ok();
    }

}
