package org.dromara.aigov.agent.service;

import org.dromara.aigov.agent.domain.vo.AigSandboxArtifactDownloadVo;
import org.dromara.aigov.agent.domain.vo.AigSandboxArtifactManifestVo;

import java.util.List;

/**
 * 沙箱产物服务（ADR-016：产物进平台）。
 *
 * <h3>核心不变式：只信账本、不信上传方</h3>
 * <p>校验基准是登记门槛时就已经存证的 {@code result.json} 里声明的 {@code path/bytes/sha256}。
 * 上传方<b>塞不进</b>清单外的文件，也<b>换不掉</b>清单内文件的内容——
 * 字节数或摘要任何一个对不上，都拒绝并说明差在哪。</p>
 *
 * <h3>产物入库不参与发布门槛</h3>
 * <p>门槛（{@code SANDBOX_RUN}）只看 {@code aig_sandbox_run}。产物入库与门槛无关：
 * 前者失败是"字节没收到"，后者失败是"作业没跑通"，两者的处置完全不同，混在一起会让
 * "产物没传"看起来像"没跑过"。</p>
 *
 * @author ai-gov
 */
public interface IAigSandboxArtifactService {

    /**
     * 一只已下载好的产物（下载响应的载荷 + 文件名）。
     *
     * @param content  字节
     * @param fileName 文件名（已做过响应头安全处理）
     * @param sha256   摘要（账本声明与服务端实算一致后的值）
     */
    record AigSandboxArtifactFile(byte[] content, String fileName, String sha256) {
    }

    /**
     * 登记一只产物的字节（逐个文件上传，ADR-016 已裁定）。
     *
     * @param sandboxRunId        沙箱运行记录ID
     * @param path                产物相对路径（必须与 result.json 声明一致）
     * @param body                字节
     * @param declaredContentType 上传方声明的类型（仅记录；下载时绝不回显）
     * @param actorId             登记人ID
     * @return 产物记录ID
     */
    Long upload(Long sandboxRunId, String path, byte[] body, String declaredContentType, Long actorId);

    /**
     * 产物清单（账本声明 + 是否已入库）。
     *
     * @param sandboxRunId 沙箱运行记录ID
     * @return 清单项（顺序与 result.json 声明一致）
     */
    List<AigSandboxArtifactManifestVo> list(Long sandboxRunId);

    /**
     * 下载一只已入库的产物。
     *
     * <p><b>每次下发都会写一条下载留痕</b>（{@code aig_sandbox_artifact_download}）：
     * 谁、何时、从哪个 IP、取走了哪一份摘要的字节。留痕写不进去就<b>拒绝下发</b>——
     * 拿不出留痕就把不可信内容交出去，正是这条要求要防的事。</p>
     *
     * @param sandboxRunId 沙箱运行记录ID
     * @param path         产物相对路径
     * @param actorId      下载人ID（取自登录态）
     * @param clientIp     来源 IP（可空）
     * @return 载荷（含文件名与摘要）
     */
    AigSandboxArtifactFile download(Long sandboxRunId, String path, Long actorId, String clientIp);

    /**
     * 某次作业的下载留痕（最新在前）。
     *
     * @param sandboxRunId 沙箱运行记录ID
     * @param limit        最多返回条数（≤0 时用默认值）
     * @return 下载记录
     */
    List<AigSandboxArtifactDownloadVo> downloads(Long sandboxRunId, int limit);

}
