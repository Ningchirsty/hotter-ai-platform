package org.dromara.talent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.talent.constant.TalentConstants;
import org.dromara.talent.domain.bo.TlExportCreateBo;
import org.dromara.talent.domain.vo.TlExportTaskVo;
import org.dromara.talent.service.ITalentExportService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人才台账导出 控制层
 *
 * <p>导出文件为受控流式下载：Controller 只把 {@link HttpServletResponse} 透传给 Service，
 * 由 Service 完成归属校验、有效期校验、响应头设置与审计。</p>
 *
 * @author talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent/export")
public class TalentExportController {

    private final ITalentExportService talentExportService;

    /**
     * 分页查询我的导出任务。
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 导出任务分页结果（不含 object_key）
     */
    @SaCheckPermission(TalentConstants.PERM_EXPORT_CREATE)
    @GetMapping("/list")
    public R<PageResult<TlExportTaskVo>> list(@Validated({Default.class, QueryGroup.class}) TlExportTaskVo query,
                                              PageQuery pageQuery) {
        return R.ok(talentExportService.queryMyPage(query, pageQuery));
    }

    /**
     * 创建导出任务（异步执行）。
     *
     * @param bo 导出条件
     * @return 新增的导出任务ID
     */
    @SaCheckPermission(TalentConstants.PERM_EXPORT_CREATE)
    @RepeatSubmit
    @PostMapping
    public R<Long> create(@Validated({Default.class, AddGroup.class}) @RequestBody TlExportCreateBo bo) {
        return R.ok(talentExportService.createExport(bo));
    }

    /**
     * 下载导出文件。
     *
     * @param exportId 导出任务ID
     * @param response HTTP 响应（由 Service 写入 Content-Type / Content-Disposition / 文件流）
     */
    @SaCheckPermission(TalentConstants.PERM_EXPORT_DOWNLOAD)
    @GetMapping("/download/{exportId}")
    public void download(@NotNull(message = "主键不能为空")
                         @PathVariable("exportId") Long exportId,
                         HttpServletResponse response) {
        talentExportService.download(exportId, response);
    }
}
