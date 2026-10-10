package org.dromara.aigov.workspace.portal.helper;

import java.util.List;

/**
 * 版本号比较（主文档线增量 2；门户"一个岗位只展示一份配置"需要它）。
 *
 * <h3>为什么不能直接比字符串</h3>
 * <p>{@code "1.9.0" > "1.10.0"} 在字符串序下成立，而在版本序下不成立。若门户用字符串比较
 * 挑"最新的那一版"，一个岗位发布到 1.10.0 之后，员工看到的仍然是 1.9.0 的卡片——
 * 界面不会报错，只是所有人都在用旧配置，且看起来"我们明明发布过了"。</p>
 *
 * <h3>为什么不用一个 SemVer 库</h3>
 * <p>这里的版本号由本平台自己写（{@code AigRolePackageValidator} 已把形态限定为
 * {@code x.y.z} 三个数字）。引入一个完整 SemVer 实现会带来预发布标签/构建号等这里
 * 根本不会出现的语义，反而让"到底按什么排序"变得难以回答。读不懂的形态一律**回退到字符串比较**
 * 并保持稳定，而不是抛异常——门户列表不该因为一条历史脏数据整页打不开。</p>
 *
 * @author ai-gov
 */
public final class AigVersionPick {

    private AigVersionPick() {
    }

    /**
     * 版本序比较：先按形态解析成三段数字逐段比，读不懂则回退字符串序。
     *
     * @param left  左
     * @param right 右
     * @return 负数/0/正数（同 {@code Comparator}）
     */
    public static int compare(String left, String right) {
        int[] a = parse(left);
        int[] b = parse(right);
        if (a == null || b == null) {
            return nullSafe(left).compareTo(nullSafe(right));
        }
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }

    /**
     * 取最大的版本号。
     *
     * @param versions 版本号列表（可空）
     * @return 最大者；列表为空/全空时返回 null
     */
    public static String max(List<String> versions) {
        String best = null;
        if (versions == null) {
            return null;
        }
        for (String version : versions) {
            if (version == null || version.isBlank()) {
                continue;
            }
            if (best == null || compare(version, best) > 0) {
                best = version;
            }
        }
        return best;
    }

    /**
     * 解析 {@code x.y.z}。
     *
     * @param version 版本号
     * @return 三段数字；形态不符返回 null
     */
    private static int[] parse(String version) {
        if (version == null) {
            return null;
        }
        String[] parts = version.trim().split("\\.");
        if (parts.length != 3) {
            return null;
        }
        int[] parsed = new int[3];
        for (int i = 0; i < 3; i++) {
            try {
                parsed[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return parsed;
    }

    /**
     * null 安全（null 当作空串，排序仍稳定）。
     *
     * @param value 值
     * @return 非 null 字符串
     */
    private static String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }

}
