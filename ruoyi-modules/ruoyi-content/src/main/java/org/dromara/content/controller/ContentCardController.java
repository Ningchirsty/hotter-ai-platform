package org.dromara.content.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.content.constant.ContentConstants;
import org.dromara.content.domain.bo.ContentCardQueryBo;
import org.dromara.content.domain.bo.ContentCardResolveBo;
import org.dromara.content.domain.vo.CpInteractionCardVo;
import org.dromara.content.service.IContentCardService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 互动确认卡 控制层。
 *
 * @author content
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/content/card")
public class ContentCardController {

    /**
     * 卡片服务
     */
    private final IContentCardService cardService;

    /**
     * 互动卡分页（支持「只看待我处理」与「只看阻断项」）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(ContentConstants.PERM_CARD_LIST)
    @GetMapping("/list")
    public R<PageResult<CpInteractionCardVo>> list(@Validated({Default.class, QueryGroup.class}) ContentCardQueryBo bo,
                                                   PageQuery pageQuery) {
        return R.ok(cardService.queryPage(bo, pageQuery));
    }

    /**
     * 处理互动卡。
     *
     * @param bo 处理入参
     * @return 处理后的卡片
     */
    @SaCheckPermission(ContentConstants.PERM_CARD_HANDLE)
    @RepeatSubmit
    @Log(title = "互动确认卡", businessType = BusinessType.UPDATE)
    @PostMapping("/resolve")
    public R<CpInteractionCardVo> resolve(@Validated @RequestBody ContentCardResolveBo bo) {
        return R.ok(cardService.resolve(bo));
    }

}
