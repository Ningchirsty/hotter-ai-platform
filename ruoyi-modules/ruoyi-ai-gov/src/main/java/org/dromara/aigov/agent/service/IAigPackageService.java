package org.dromara.aigov.agent.service;

import org.dromara.aigov.agent.domain.bo.AigPackageUploadBo;
import org.dromara.aigov.agent.domain.vo.AigPackageDisableVo;
import org.dromara.aigov.agent.domain.vo.AigPackageInstallLogVo;
import org.dromara.aigov.agent.domain.vo.AigPackageInstallVo;
import org.dromara.aigov.agent.domain.vo.AigPackageRegisterVo;
import org.dromara.aigov.agent.domain.vo.AigPackageStatusVo;

import java.util.List;

/**
 * Package 上传登记与安装（设计 §6.1、§6.3）。
 *
 * <p><b>三个写入口，各自对应一个权限点</b>：{@link #register}（上传登记）、
 * {@link #install}（安装）、{@link #disable}（停用）。分开的理由是「上传的人」与「决定要不要装的人」
 * 未必是同一个，而「决定装」与「决定下线」承担的影响面也完全不同——前者建出 DRAFT 版本（还没人用），
 * 后者可能把一个正在被业务使用的 STABLE 版本撤下来。</p>
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
     * 停用：把该 Package 版本带进来的 Agent/Skill 版本批量下线（设计 §6.3「失败则停用或回滚」）。
     *
     * <p>判据与边界：</p>
     * <ol>
     *     <li>停用对象 = <b>回指该 Package 版本的版本行</b>（{@code package_version_id} 判定），
     *         与安装的幂等判据同源。同一 Agent 的<b>其他</b>版本一律不动——它们来自别的包或平台内置，
     *         不能因为「同一个 Agent 编码」被连坐下线；</li>
     *     <li><b>走发布状态的唯一写入口</b>（{@code IAigAgentRegistryService#advanceRelease}），
     *         因此每个版本都会：按状态机判定边是否合法（终态不能停用）、CAS 更新（并发不覆盖）、
     *         追加一条发布事件。本服务<b>不直接改</b> {@code release_status}；</li>
     *     <li>已在停用状态的版本按<b>幂等</b>处理，全部如此时不改任何东西、也不写账本
     *         （与安装的幂等命中一致：账本记的是发生过的动作，不是重复的意图）；</li>
     *     <li>已是终态（{@code ARCHIVED}）的版本<b>跳过并说明原因</b>，不让整批失败——
     *         一个归档版本不该阻断其余版本的下线；但<b>一条也停不了</b>时报错；</li>
     *     <li>必须已经装过：没有任何版本回指该 Package 版本时报错，而不是静默成功
     *         ——否则账本里会出现「停用了一个从没装过的包版本」这种说不清的行。</li>
     * </ol>
     *
     * <p><b>停用不等于删除</b>：版本内容与原文哈希都不动，只是发布状态变成 DISABLED。
     * 重新启用走发布推进（{@code DISABLED → STABLE} 需证明该版本曾 STABLE 过）。</p>
     *
     * @param packageVersionId Package 版本ID
     * @param operatorId       操作人
     * @return 停用结果（成功项与跳过项分开，跳过项带原因）
     */
    AigPackageDisableVo disable(Long packageVersionId, Long operatorId);

    /**
     * <b>包级</b>停用：把 {@code aig_package.status} 置为停用，此后<b>不再接受新版本上传</b>，
     * 也<b>不能安装</b>该包的版本。
     *
     * <p><b>与 {@link #disable(Long, Long)} 的分工（必须分清，否则会「误以为停了」）</b>：</p>
     * <table border="1">
     *     <caption>两个「停用」</caption>
     *     <tr><th></th><th>{@link #disablePackage}（包级）</th><th>{@link #disable}（版本级）</th></tr>
     *     <tr><td>改什么</td><td>{@code aig_package.status}</td>
     *         <td>该 Package 版本带进来的 Agent/Skill 版本的发布状态</td></tr>
     *     <tr><td>影响谁</td><td><b>将来</b>：不许上传新版本、不许安装</td>
     *         <td><b>已经装出去、可能正在被业务使用的</b>那些版本</td></tr>
     *     <tr><td>会不会动线上</td><td><b>不会</b></td><td>会（STABLE 版本会被下线）</td></tr>
     * </table>
     *
     * <p>两者刻意不互相隐含：包级停用<b>不会</b>顺手把已装的版本下线——那是个影响面大得多的动作，
     * 必须由人显式做（调用方要「一停到底」就两个都调，页面也是这么引导的）。</p>
     *
     * <p>幂等：已是停用状态时返回 {@code changed=false}，不改库、不报错。</p>
     *
     * @param packageId  Package ID
     * @param operatorId 操作人
     * @return 变更结果（含「下一步该做什么」的说明）
     */
    AigPackageStatusVo disablePackage(Long packageId, Long operatorId);

    /**
     * <b>包级</b>启用：把 {@code aig_package.status} 置回正常，恢复「可上传新版本、可安装」。
     *
     * <p>与 {@link #disablePackage} 是同一影响面的一组动作（都是改变这个包还能不能被使用），
     * 因此共用同一个权限点。同样幂等。</p>
     *
     * <p>注意它<b>不会</b>把版本级停用过的版本重新启用：版本重新启用走发布推进
     * （{@code DISABLED → STABLE} 需证明该版本曾 STABLE 过）。</p>
     *
     * @param packageId  Package ID
     * @param operatorId 操作人
     * @return 变更结果
     */
    AigPackageStatusVo enablePackage(Long packageId, Long operatorId);

    /**
     * 查某个 Package 版本的安装日志（追加型账本，按时间正序）。
     *
     * @param packageVersionId Package 版本ID
     * @return 日志清单
     */
    List<AigPackageInstallLogVo> listInstallLog(Long packageVersionId);

}
