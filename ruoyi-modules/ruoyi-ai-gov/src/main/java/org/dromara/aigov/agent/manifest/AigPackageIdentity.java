package org.dromara.aigov.agent.manifest;

/**
 * 包记录侧的身份声明（来自 {@code aig_package} 与 {@code aig_package_version}），用于与 Manifest 交叉核对。
 *
 * <p><b>为什么需要这一层</b>：Manifest 是<b>提交方自己写的</b>一份声明，它说自己是「design-center 发布的
 * Apache-2.0 包」，这只是它的说法。真正有独立来源的事实是包记录：包体上传时算的 {@code checksum}、
 * 登记时的 {@code publisher} 与 {@code license_code}、以及入库时对原始字节算的 {@code manifest_hash}。
 * 「说法」与「事实」不一致时，命中的是 §6.2-4（来源、校验和或版权信息不明确）——
 * 而不是「以其中一方为准」：以 Manifest 为准会把包记录的登记变成摆设，
 * 以包记录为准则等于默默放行一份描述别的包的 Manifest。</p>
 *
 * <p>刻意做成 record 而不是直接传实体：校验器保持纯函数（可单测、无 MyBatis 依赖），
 * 调用方负责把库里两行的值取出来装配。</p>
 *
 * @param packageCode        包编码（{@code aig_package.package_code}）
 * @param publisher          发布方（{@code aig_package.publisher}）
 * @param licenseCode        许可证（{@code aig_package.license_code}）
 * @param checksum           包体 SHA-256（{@code aig_package.checksum}），与 Manifest 的 {@code checksum} 比对
 * @param packageType        包类型（{@code aig_package.package_type}）
 * @param version            版本号（{@code aig_package_version.version}）
 * @param storedManifestHash 入库时对 Manifest 原文算的 SHA-256（{@code aig_package_version.manifest_hash}），
 *                           用于发现「入库后原文被改动」
 * @author ai-gov
 */
public record AigPackageIdentity(
    String packageCode,
    String publisher,
    String licenseCode,
    String checksum,
    String packageType,
    String version,
    String storedManifestHash
) {
}
