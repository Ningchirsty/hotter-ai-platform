package org.dromara.content.controller;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.constant.ContentConstants;
import org.dromara.content.domain.bo.ContentOutputCheckQueryBo;
import org.dromara.content.domain.vo.CpOutputCheckVo;
import org.dromara.content.service.IContentOutputCheckService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 成品一致性检查 控制层。
 *
 * <p>能力：把「生成的结果图」与「原参考图」比对，判断成品是否忠实还原参考图，
 * 覆盖任务出稿之后的验收环节。结论不回流为产品事实（SPEC §0.1 红线第 2 条）。</p>
 *
 * @author content
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/content/check")
public class ContentOutputCheckController {

    /**
     * 检查服务
     */
    private final IContentOutputCheckService checkService;

    /**
     * 检查记录分页。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(ContentConstants.PERM_CHECK_LIST)
    @GetMapping("/list")
    public R<PageResult<CpOutputCheckVo>> list(@Validated({Default.class, QueryGroup.class}) ContentOutputCheckQueryBo bo,
                                               PageQuery pageQuery) {
        return R.ok(checkService.queryPage(bo, pageQuery));
    }

    /**
     * 某任务下的检查记录。
     *
     * @param taskId 任务ID
     * @return 检查列表
     */
    @SaCheckPermission(ContentConstants.PERM_CHECK_QUERY)
    @GetMapping("/byTask/{taskId}")
    public R<List<CpOutputCheckVo>> byTask(@NotNull(message = "任务ID不能为空") @PathVariable("taskId") Long taskId) {
        return R.ok(checkService.listByTask(taskId));
    }

    /**
     * 检查详情。
     *
     * @param checkId 检查ID
     * @return 详情
     */
    @SaCheckPermission(ContentConstants.PERM_CHECK_QUERY)
    @GetMapping("/{checkId}")
    public R<CpOutputCheckVo> getInfo(@NotNull(message = "检查ID不能为空") @PathVariable("checkId") Long checkId) {
        return R.ok(checkService.getDetail(checkId));
    }

    /**
     * 发起检查（上传参考图与成品图，异步比对）。
     *
     * <p><b>刻意不加 {@code @RepeatSubmit}</b>：该注解的防重 key 取自入参的字符串化，
     * 而 {@code MultipartFile.toString()} 既不含文件名也不含内容——同一批两张不同图片
     * 会算出相同的 key 而被误判为重复提交（内容任务上传附件处已踩过同一个坑）。</p>
     *
     * @param taskId          任务ID
     * @param referenceFileId 已有参考图附件ID（可空；与 referenceFile 二选一）
     * @param referenceFile   新上传的参考图（可空）
     * @param resultFile      成品图（必填）
     * @param remark          备注（可空）
     * @return 检查ID
     */
    @SaCheckPermission(ContentConstants.PERM_CHECK_RUN)
    @Log(title = "成品一致性检查", businessType = BusinessType.INSERT)
    @PostMapping("/run")
    public R<Long> run(@RequestParam("taskId") Long taskId,
                       @RequestParam(value = "referenceFileId", required = false) Long referenceFileId,
                       @RequestParam(value = "referenceFile", required = false) MultipartFile referenceFile,
                       @RequestParam("resultFile") MultipartFile resultFile,
                       @RequestParam(value = "remark", required = false) String remark) {
        return R.ok(checkService.run(taskId, referenceFileId, referenceFile, resultFile, remark));
    }

    /**
     * 删除检查记录（逻辑删除）。
     *
     * @param checkId 检查ID
     * @return 操作结果
     */
    @SaCheckPermission(ContentConstants.PERM_CHECK_REMOVE)
    @Log(title = "成品一致性检查", businessType = BusinessType.DELETE)
    @DeleteMapping("/{checkId}")
    public R<Void> remove(@NotNull(message = "检查ID不能为空") @PathVariable("checkId") Long checkId) {
        checkService.remove(checkId);
        return R.ok();
    }

    /**
     * 读取检查涉及的图片（页面并排预览用）。
     *
     * <p>走本接口而不是对象存储直链：内容资料存在私有前缀且不登记 {@code sys_oss}，
     * 直链会绕过内容模块的授权（见 {@code ContentOssHelper} 的安全约定）。</p>
     *
     * @param checkId 检查ID
     * @param side    {@code reference} 或 {@code result}
     * @return 图片字节
     */
    @SaCheckPermission(ContentConstants.PERM_CHECK_QUERY)
    @GetMapping("/{checkId}/image/{side}")
    public ResponseEntity<byte[]> image(@NotNull(message = "检查ID不能为空") @PathVariable("checkId") Long checkId,
                                        @PathVariable("side") String side) {
        IContentOutputCheckService.CheckImage image = checkService.loadImage(checkId, side);
        String encoded = URLEncoder.encode(image.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, image.mimeType())
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded)
            .contentType(MediaType.parseMediaType(image.mimeType()))
            .body(image.bytes());
    }

}
