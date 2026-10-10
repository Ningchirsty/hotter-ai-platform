package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 沙箱运行证据的**可信度来源**（{@code aig_sandbox_run.attestation}）。
 *
 * <p><b>为什么需要一个显式的枚举，而不是一个 boolean</b>：要回答的不是"好不好"，而是
 * "这条证据是怎么来的"。将来可能有第三种来源（例如平台自己起的执行器、或独立主机上的
 * 执行器签名），布尔值到时候只能推翻重来。</p>
 *
 * <p><b>它存在的理由是不让人误读</b>：{@link #UNATTESTED} 的证据能证明"有人提交了这份
 * result.json、提交后没被改过、字段自洽"，<b>不能</b>证明"这份结果来自一次真实运行"。
 * 登记人可以在文本框里编一条字段自洽的记录，沙箱一次都不用跑——这件事必须在库里、
 * 接口里、界面上都看得见（决定见 ADR-015「已知边界」）。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigSandboxAttestationEnum {

    /**
     * 人工登记，**无密码学保证**。当前所有证据都是这一种。
     */
    UNATTESTED("UNATTESTED", "人工登记（无密码学保证，不证明真的跑过）"),

    /**
     * 执行器私钥签名且平台公钥验签通过。
     *
     * <p><b>尚未实现</b>：没有任何代码路径会写入这个值。实现时的要点（见 ADR-015）：
     * 私钥只有宿主机 root 能读、平台只持有公钥（能验不能造）、签名必须与"真的跑过"绑定。</p>
     */
    SIGNED("SIGNED", "执行器私钥签名 + 平台公钥验签（未实现）");

    /**
     * 入库值
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举或 null
     */
    public static AigSandboxAttestationEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigSandboxAttestationEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 是否为"有密码学保证"的证据。
     *
     * @return 仅 {@link #SIGNED} 为 true
     */
    public boolean attested() {
        return this == SIGNED;
    }

}
