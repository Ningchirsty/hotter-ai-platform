package org.dromara.aigov.agent.service;

import org.dromara.aigov.agent.domain.bo.AigPackageUploadBo;
import org.dromara.aigov.agent.domain.vo.AigPackageInstallLogVo;
import org.dromara.aigov.agent.domain.vo.AigPackageInstallVo;
import org.dromara.aigov.agent.domain.vo.AigPackageRegisterVo;

import java.util.List;

/**
 * Package 上传登记与安装（设计 §6.1、§6.3）。
 *
 * <p><b>两个写入口，各自对应一个权限点</b>：{@link #register}（上传登记）与
 * {@link #install}（安装）。分开的理由是「上传的人」与「决定要不要装的人」未必是同一个，
 * 而且安装会真的建出 Agent/Skill 版本行。</p>
 *
 * <p>本阶段**只支持声明式 Package**（Q9）：安装只读 Manifest 里的声明，
 * 不执行任何代码、不落包体。</p>
 *
 * @author ai-gov
 */
public interface IAigPackageService {

    /**
     * 上传登记：携包体 + Manifest 原文，登记包与版本。
     *
     * <p>顺序与判据：</p>
     * <ol>
     *     <li>包体必须存在且在大小上限内——**要求携包体是刻意的**：服务端据此计算 SHA-256 并与
     *         Manifest 声明的 {@code checksum} 比对，校验和因此不再是「调用方说了算」；</li>
     *     <li>Manifest 必须能解析（解析不出来就没有可登记的东西）；</li>
     *     <li>校验和必须一致，否则整笔拒绝、不落库——不一致说明「这份包体」与「这份 Manifest」
     *         不是一对；</li>
     *     <li>登记包（不存在则建）与版本（`DRAFT`），并把这次校验的结论写进
     *         {@code scan_result/scan_detail}——**被拒绝也要留痕**（建表注释里的
     *         {@code idx_aig_pkg_ver_scan} 就是给「查被拒的版本」用的）；</li>
     *     <li>版本重复（同包同版本号）直接拒绝：**版本不可变，不允许覆盖**。</li>
     * </ol>
     *
     * @param bo        上传入参（Manifest 原文 + 来源引用）
     * @param body      包体字节
     * @param bodyName  包体文件名（仅用于留痕）
     * @param operatorId 操作人
     * @return 登记结果（含这次校验的结论）
     */
    AigPackageRegisterVo register(AigPackageUploadBo bo, byte[] body, String bodyName, Long operatorId);

    /**
     * 安装：按 Manifest 声明建出 Agent/Skill 版本。
     *
     * <p>判据：</p>
     * <ol>
     *     <li>Package 与版本必须存在，且包未被停用；</li>
     *     <li><b>{@code scan_result} 必须是 PASS</b>——这里查的是库里的证据，不是调用方声明；</li>
     *     <li>Manifest 必须声明了内容物（agents/skills），否则明确报「没有内容可装」；</li>
     *     <li>**幂等**：同一个 Package 版本装过就不再重复建（以「已存在指向该 Package 版本的
     *         Agent/Skill 版本行」为判据，而不是看日志——日志是审计，不是状态）；</li>
     *     <li>装出来的版本一律 `DRAFT`：**安装不等于发布，不跳门槛**。</li>
     * </ol>
     *
     * @param packageVersionId Package 版本ID
     * @param operatorId       操作人
     * @return 安装结果
     */
    AigPackageInstallVo install(Long packageVersionId, Long operatorId);

    /**
     * 查某个 Package 版本的安装日志（追加型账本，按时间正序）。
     *
     * @param packageVersionId Package 版本ID
     * @return 日志清单
     */
    List<AigPackageInstallLogVo> listInstallLog(Long packageVersionId);

}
