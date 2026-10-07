package org.dromara.aigov.agent.domain.vo;

/**
 * Package <b>包级</b>状态变更结果（停用/启用这个包本身）。
 *
 * <p><b>与 {@code AigPackageDisableVo} 不是一回事</b>，别混用：</p>
 * <ul>
 *     <li>本对象是<b>包级</b>：改的是 {@code aig_package.status}，作用是「不再接受新版本上传、
 *         也不能安装新版本」——影响的是<b>将来</b>；</li>
 *     <li>{@code AigPackageDisableVo} 是<b>版本级</b>：把该 Package 版本带进来的
 *         Agent/Skill 版本批量下线——影响的是<b>已经装出去、可能正在被使用的</b>东西。</li>
 * </ul>
 * <p>两者刻意不互相隐含：只做包级停用，线上正在跑的东西不会停；要停它们必须显式走版本级停用。
 * 把「误以为停了」这类事挡在接口语义这一层。</p>
 *
 * @param packageId   Package ID
 * @param packageCode 包编码
 * @param disabled    变更后的状态（true = 已停用）
 * @param changed     本次是否真的改了库（false = 幂等命中，已是目标状态）
 * @param note        可读说明（含下一步该做什么）
 * @author ai-gov
 */
public record AigPackageStatusVo(
    Long packageId,
    String packageCode,
    boolean disabled,
    boolean changed,
    String note
) {
}
