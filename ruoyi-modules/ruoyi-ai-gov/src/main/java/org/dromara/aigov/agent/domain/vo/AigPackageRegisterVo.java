package org.dromara.aigov.agent.domain.vo;

import org.dromara.aigov.agent.manifest.AigPackageManifest;

/**
 * Package 上传登记结果。
 *
 * <p><b>被拒绝不是异常，而是一个结论</b>：上传请求本身是成功的（请求合法、包体哈希与 Manifest
 * 声明一致），被拒的是这份 Manifest。所以 {@code packageId/packageVersionId} 仍然有值
 * （拒绝也要留痕、能在清单里查到、能看到命中哪条规则），只是 {@code scanPass=false}
 * 且 {@code scanDetail} 说明原因。前端据此渲染「已登记但被拒」，
 * 而不是把它当网络错误处理。</p>
 *
 * @param packageId        Package ID
 * @param packageVersionId Package 版本ID
 * @param packageCode      包编码
 * @param version          版本号
 * @param checksum         包体 SHA-256（服务端对上传字节计算，并与 Manifest 声明比对过）
 * @param manifestHash     Manifest 原文 SHA-256（对入库的那串字节计算）
 * @param scanPass         上传时那次校验是否通过
 * @param scanResult       PASS / REJECT
 * @param scanDetail       校验说明（拒绝时写明命中哪一条规则）
 * @param manifest         Manifest 解析视图（页面可直接展示声明内容；无法解析时为 null）
 * @author ai-gov
 */
public record AigPackageRegisterVo(
    Long packageId,
    Long packageVersionId,
    String packageCode,
    String version,
    String checksum,
    String manifestHash,
    boolean scanPass,
    String scanResult,
    String scanDetail,
    AigPackageManifest manifest
) {
}
