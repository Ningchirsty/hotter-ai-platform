package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.agent.domain.vo.AigSandboxArtifactManifestVo;
import org.dromara.aigov.agent.helper.AigSandboxArtifactManifest;
import org.dromara.aigov.agent.service.IAigSandboxArtifactService;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 沙箱产物接口（ADR-016：产物进平台）。
 *
 * <h3>下载的三条硬要求（少一条就是"在平台域下执行外部代码"的通道）</h3>
 * <ol>
 *     <li>{@code Content-Type: application/octet-stream}——<b>绝不回显上传方声明的类型</b>。
 *         产物由不可信代码在沙箱里产生，可以是 HTML/SVG/JS；回显 {@code text/html} 就是内联渲染；</li>
 *     <li>{@code Content-Disposition: attachment}——浏览器只下载、不渲染；</li>
 *     <li>{@code X-Content-Type-Options: nosniff}——阻止浏览器"猜"类型后按 HTML 处理。</li>
 * </ol>
 * <p>文件名同时给 ASCII 形式与 RFC 5987 形式：前者兜底，后者让中文名可读。</p>
 *
 * <h3>权限沿用两档</h3>
 * <p>登记用 {@code aig:sandbox:record}（与门槛登记同一批人），列表/下载用 {@code aig:sandbox:list}。
 * 按 ADR-016 的裁定**不新开权限点**——这里没有新的角色边界，多一个权限点只会多一处要维护的授权。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/sandbox")
public class AigSandboxArtifactController {

    private final IAigSandboxArtifactService sandboxArtifactService;

    /**
     * 登记一只产物的字节（multipart：{@code path} + 文件）。
     *
     * @param runId       沙箱运行记录ID
     * @param path        产物相对路径（必须与账本声明一致）
     * @param file        文件
     * @param contentType 上传方声明的类型（仅记录）
     * @return 产物记录ID
     */
    @RepeatSubmit
    @SaCheckPermission(AigConstants.PERM_SANDBOX_RECORD)
    @PostMapping("/run/{runId:\\d+}/artifact")
    public R<Long> upload(@PathVariable Long runId,
                          @RequestParam String path,
                          @RequestParam("file") MultipartFile file,
                          @RequestParam(required = false) String contentType) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("产物文件为空：请选择要登记的文件");
        }
        byte[] body;
        try {
            body = file.getBytes();
        } catch (IOException e) {
            throw new ServiceException("读取上传文件失败：" + e.getClass().getSimpleName());
        }
        return R.ok(sandboxArtifactService.upload(runId, path, body, contentType,
            LoginHelper.getUserId()));
    }

    /**
     * 产物清单（账本声明 + 是否已入库）。
     *
     * @param runId 沙箱运行记录ID
     * @return 清单项
     */
    @SaCheckPermission(AigConstants.PERM_SANDBOX_LIST)
    @GetMapping("/run/{runId:\\d+}/artifacts")
    public R<List<AigSandboxArtifactManifestVo>> list(@PathVariable Long runId) {
        return R.ok(sandboxArtifactService.list(runId));
    }

    /**
     * 下载一只已入库的产物（响应头见类注释的三条硬要求）。
     *
     * @param runId 沙箱运行记录ID
     * @param path  产物相对路径
     * @return 字节响应
     */
    @SaCheckPermission(AigConstants.PERM_SANDBOX_LIST)
    @GetMapping("/run/{runId:\\d+}/artifact")
    public ResponseEntity<byte[]> download(@PathVariable Long runId, @RequestParam String path) {
        IAigSandboxArtifactService.AigSandboxArtifactFile file =
            sandboxArtifactService.download(runId, path);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(file.fileName()));
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(file.content());
    }

    /**
     * 组装 {@code Content-Disposition}（ASCII 兜底 + RFC 5987 可读名）。
     *
     * @param fileName 文件名（已经过响应头安全处理）
     * @return 头值
     */
    private String contentDisposition(String fileName) {
        String ascii = fileName.replaceAll("[^\\x20-\\x7e]", "_");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''"
            + AigSandboxArtifactManifest.rfc5987(fileName);
    }

}
