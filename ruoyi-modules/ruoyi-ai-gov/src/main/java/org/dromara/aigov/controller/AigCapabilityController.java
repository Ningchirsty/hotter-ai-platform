package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.domain.bo.AigCapabilityBo;
import org.dromara.aigov.domain.vo.AigCapabilityOverviewVo;
import org.dromara.aigov.domain.vo.AigCapabilityVo;
import org.dromara.aigov.service.IAigCapabilityOverviewService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    private final IAigCapabilityOverviewService capabilityOverviewService;

    /**
     * 能力「配置完整度」聚合清单（能力卡片与看板用）。
     *
     * <p><b>为什么单独一个接口而不是前端拼</b>：卡片要显示"候选 N · 策略 M/4 · 近 24h 调用"，
     * 并且要能按"缺少模型候选"这类问题<b>筛选</b>——筛选必须下发到服务端，
     * 否则前端只能拿一页数据在本地筛，翻页后结论就错了。</p>
     *
     * <p>权限沿用 {@code aig:capability:list}：它返回的是配置完整度与调用计数，
     * 与能力清单是同一类信息，不额外暴露正文字段。</p>
     *
     * @param bo             基础筛选（能力编码 / 名称 / 状态）
     * @param requiredTags   标签模糊筛选（可空）
     * @param issueCode      只看带某类问题的能力（如 {@code NO_MODEL_CANDIDATE}；可空）
     * @param candidateState 候选状态筛选：{@code HAS} / {@code NO}（可空）
     * @return 概览列表（有问题的在前）
     */
    @SaCheckPermission(AigConstants.PERM_CAPABILITY_LIST)
    @GetMapping("/overview")
    public R<List<AigCapabilityOverviewVo>> overview(
        @Validated({Default.class, QueryGroup.class}) AigCapabilityBo bo,
        @RequestParam(required = false) String requiredTags,
        @RequestParam(required = false) String issueCode,
        @RequestParam(required = false) String candidateState) {
        return R.ok(capabilityOverviewService.overview(bo, requiredTags, issueCode, candidateState));
    }

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
