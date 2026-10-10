package org.dromara.aigov.workspace.helper;

import org.dromara.aigov.studio.helper.AigStudioContentHasher;

/**
 * 岗位包清单的哈希（{@code aig_role_version.manifest_sha256}）。
 *
 * <h3>它要回答什么</h3>
 * <ul>
 *     <li><b>完整性</b>：这份已发布版本的清单有没有被改过（发布后不可变）；</li>
 *     <li><b>"有未发布改动"</b>：编辑中的清单与最近一次发布的清单是否一致。</li>
 * </ul>
 * <p>两者都要求哈希对<b>序列化细节</b>不敏感（键顺序、空白、1 与 1.0）。
 * 否则界面会对一份没变的清单喊"你有未发布改动"，反过来真改动被判成没变。</p>
 *
 * <h3>为什么直接复用训练台那份实现，而不是再写一份</h3>
 * <p>规范化规则在本平台<b>只应有一份实现</b>：同一串内容在两处算出不同哈希，
 * 是那种"两边各自都能跑、合起来才出错"的问题。训练台那份
 * （{@link AigStudioContentHasher}）已经是既有的规范化 JSON 实现，且被 13 条用例钉住
 * （键顺序/空白/嵌套排序/数值归一/转义/幂等/非法输入）。这里只做一个<b>语义明确的入口</b>：
 * 调用方读代码时知道"这是岗位包清单的哈希"，而不是去看一个叫 Studio 的类。
 * 若将来把规范化提出到中立包，改动点只有这里与训练台那一处。</p>
 *
 * @author ai-gov
 */
public final class AigRoleManifestHasher {

    private AigRoleManifestHasher() {
    }

    /**
     * 计算岗位包清单的哈希（规范化 JSON 的 sha256，十六进制小写）。
     *
     * @param manifestJson 清单 JSON；null 按 JSON null 处理
     * @return 十六进制小写 sha256
     * @throws IllegalArgumentException 入参为空白串或不是合法 JSON
     */
    public static String hash(String manifestJson) {
        return AigStudioContentHasher.hash(manifestJson);
    }

    /**
     * 规范化清单 JSON（键排序、去无意义空白、数值归一）。
     *
     * @param manifestJson 清单 JSON；null 返回 {@code "null"}
     * @return 规范化后的 JSON
     * @throws IllegalArgumentException 入参为空白串或不是合法 JSON
     */
    public static String canonicalize(String manifestJson) {
        return AigStudioContentHasher.canonicalize(manifestJson);
    }

}
