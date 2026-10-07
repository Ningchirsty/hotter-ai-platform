package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Package 安装链路的动作（{@code aig_package_install_log.action}）。
 *
 * <p>这张表是<b>追加型账本</b>（一行一个动作、不更新不删除），它回答的是
 * 「这个包是什么时候、被谁、以什么结果走过链路的」。因此动作必须是个受控取值，
 * 而不是各写各的自由字符串——否则统计与追责都做不了。</p>
 *
 * <p>建表注释里列了链路动作（UPLOAD / SCAN / SANDBOX / RUN_CASES / APPROVE /
 * PUBLISH_CANDIDATE / PUBLISH_STABLE / DISABLE / ROLLBACK）；
 * 本枚举在此基础上补了 {@link #INSTALL}：<b>安装</b>（把 Package 的内容物建成 Agent/Skill 版本）
 * 是链路里真实存在的一步，之前没有对应的动作编码，只能塞进别的动作里——那会让账本说不清
 * 「这行到底是上传还是安装」。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigPackageInstallActionEnum {

    /**
     * 上传并登记包与版本
     */
    UPLOAD("UPLOAD", "上传登记"),

    /**
     * 安装（把声明的内容物建成 Agent/Skill 版本）
     */
    INSTALL("INSTALL", "安装"),

    /**
     * 停用/回滚带进来的版本
     */
    DISABLE("DISABLE", "停用");

    /**
     * 编码（入库值）
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
     * @return 枚举；未命中返回 null
     */
    public static AigPackageInstallActionEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigPackageInstallActionEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
