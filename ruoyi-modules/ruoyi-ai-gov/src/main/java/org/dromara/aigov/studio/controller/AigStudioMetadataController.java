package org.dromara.aigov.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.studio.domain.vo.AigStudioMetadataVo;
import org.dromara.aigov.studio.service.IAigStudioDraftService;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 训练台元数据接口（设计 §9.2 {@code GET /aigov/studio/metadata}）。
 *
 * <p><b>为什么单独一个控制器而不是塞进 {@code /drafts}</b>：元数据是"训练台支持哪些类别/分节"
 * 这类<b>与某一份草稿无关</b>的信息，把它挂在 {@code /drafts} 下会让人以为是草稿级资源。
 * 设计给的路径就是 {@code /aigov/studio/metadata}。</p>
 *
 * <p>只读，因此不需要防重；权限沿用草稿查看权限（能看到训练台的人就能看到词表）。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/studio")
public class AigStudioMetadataController extends BaseController {

    private final IAigStudioDraftService draftService;

    /**
     * 取训练台元数据：可选类别与 Prompt 分节（服务端唯一词表来源）。
     *
     * @return 元数据
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_DRAFT_QUERY)
    @GetMapping("/metadata")
    public R<AigStudioMetadataVo> metadata() {
        return R.ok(draftService.metadata());
    }

}
