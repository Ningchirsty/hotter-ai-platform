package org.dromara.aigov.task.helper;

import org.dromara.aigov.task.config.AigArtifactProperties;
import org.dromara.aigov.task.domain.bo.AigTaskArtifactBo;
import org.dromara.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 制品登记的语义校验（纯函数，无 Spring 依赖）。
 *
 * <p><b>为什么把校验抽出来单独一类</b>：这些规则决定「什么能进制品账本」，
 * 而它们的失败形态是<b>安静地放行</b>——清单比对写成包含、大小写成 >、路径判断漏掉
 * Windows 盘符，都会表现为「某类不该收的制品收了」。抽成纯函数后，每条规则的正反用例
 * 可以直接钉住；服务层只剩编排（查任务、写证据、写事件）。</p>
 *
 * <p><b>失败原因一次列全</b>：生产方一次提交可能同时踩两条（比如 MIME 不在清单 + 哈希形态不对）。
 * 只报第一条会让对方改一次、再来一次，来回几轮才发现全部问题——这类往返成本最后都变成
 * 「接入很麻烦」的口碑。{@link #check} 因此把全部原因拼成一条明细。</p>
 *
 * <p><b>两处归一化（不是放宽）</b>：{@code mimeType} 与 {@code sha256} 统一trim+小写。
 * 契约里 sha256 的形态是 {@code ^[0-9a-f]{64}$}（小写）；生产方从某些库拿到大写十六进制
 * 是常见的，因为大小写不同就拒收属于无谓的摩擦，而归一化后比较与检索才是一致的。
 * MIME 类型按 RFC 本身大小写不敏感，同理。</p>
 *
 * @author ai-gov
 */
public final class AigArtifactValidator {

    /**
     * sha256 的契约形态（与 contract/execution-result.schema.json 的 pattern 同口径）。
     */
    private static final String SHA256_PATTERN = "^[0-9a-f]{64}$";

    /**
     * MIME 族通配后缀（{@code image/*}）。
     */
    private static final String MIME_FAMILY_SUFFIX = "/*";

    /**
     * 制品类型长度上限（契约：type maxLength 32）。
     */
    private static final int TYPE_MAX = 32;

    /**
     * 对象引用长度上限（与 DDL 的 varchar(512) 一致）。
     */
    private static final int STORAGE_REF_MAX = 512;

    private AigArtifactValidator() {
    }

    /**
     * 校验一次制品登记。
     *
     * @param bo         入参
     * @param properties 策略（允许的 MIME、大小上限）
     * @return 校验结论（含归一化后的 MIME 与 sha256）
     */
    public static AigArtifactCheck check(AigTaskArtifactBo bo, AigArtifactProperties properties) {
        if (bo == null) {
            return new AigArtifactCheck(false, "入参不能为空", null, null, null, null);
        }
        List<String> problems = new ArrayList<>();

        String type = trim(bo.getArtifactType());
        if (StringUtils.isBlank(type)) {
            problems.add("制品类型不能为空");
        } else if (type.length() > TYPE_MAX) {
            problems.add("制品类型长度 " + type.length() + " 超过 " + TYPE_MAX);
        }

        String mime = trim(bo.getMimeType()).toLowerCase(Locale.ROOT);
        if (StringUtils.isBlank(mime)) {
            problems.add("MIME 类型不能为空");
        } else {
            problems.addAll(checkMime(mime, properties));
        }

        Long size = bo.getSizeBytes();
        if (size == null) {
            problems.add("制品大小不能为空");
        } else if (size < 0) {
            problems.add("制品大小不能为负数（传入 " + size + "）");
        } else if (properties != null && size > properties.getMaxSizeBytes()) {
            problems.add("制品大小 " + size + " 字节超过上限 " + properties.getMaxSizeBytes() + " 字节");
        }

        String sha = trim(bo.getSha256()).toLowerCase(Locale.ROOT);
        if (StringUtils.isBlank(sha)) {
            problems.add("制品 sha256 不能为空");
        } else if (!sha.matches(SHA256_PATTERN)) {
            // 长度与字符集分开报：只说「必须是 64 位小写十六进制」，拿到 64 位非十六进制的人
            // 会去数字数，把真正的原因（字符不对）看漏
            problems.add(sha.length() == 64
                ? "制品 sha256 含非十六进制字符（必须是 0-9a-f）"
                : "制品 sha256 必须是 64 位小写十六进制（当前长度 " + sha.length() + "）");
        }

        String ref = trim(bo.getStorageRef());
        if (StringUtils.isBlank(ref)) {
            problems.add("制品对象引用不能为空");
        } else {
            problems.addAll(checkStorageRef(ref));
        }

        boolean passed = problems.isEmpty();
        return new AigArtifactCheck(passed, passed ? null : String.join("；", problems), type, mime, sha, ref);
    }

    /**
     * 校验 MIME 是否在允许清单内。
     *
     * @param mime       已小写的 MIME
     * @param properties 策略
     * @return 问题清单（可能为空）
     */
    private static List<String> checkMime(String mime, AigArtifactProperties properties) {
        List<String> problems = new ArrayList<>();
        if (mime.length() > 128) {
            problems.add("MIME 类型长度 " + mime.length() + " 超过 128");
            return problems;
        }
        if (!mime.contains("/")) {
            problems.add("MIME 类型形态不对（缺 \"类型/子类型\"）：" + mime);
            return problems;
        }
        List<String> allowed = properties == null ? null : properties.getAllowedMimeTypes();
        if (allowed == null || allowed.isEmpty()) {
            // 空清单 = 全部拒绝，且要把原因说清：否则读到这条的人会以为「平台坏了」
            problems.add("允许清单为空，按策略拒绝一切制品（请检查 aigov.artifact.allowed-mime-types）");
            return problems;
        }
        for (String item : allowed) {
            if (StringUtils.isBlank(item)) {
                continue;
            }
            String rule = item.trim().toLowerCase(Locale.ROOT);
            if (rule.endsWith(MIME_FAMILY_SUFFIX)) {
                String family = rule.substring(0, rule.length() - 1);
                if (mime.startsWith(family)) {
                    return problems;
                }
            } else if (rule.equals(mime)) {
                return problems;
            }
        }
        problems.add("MIME 类型 " + mime + " 不在允许清单内（允许：" + String.join(", ", allowed) + "）");
        return problems;
    }

    /**
     * 校验对象引用不是服务器本地路径。
     *
     * <p>依据设计 §9 的口径：「业务表只存资产 ID / 对象引用，不存服务器本地路径」。
     * 本地路径在这里比别处更危险——制品账本是<b>跨进程、跨主机</b>被读的
     * （后端上一个版本、镜像里的另一个容器、运维在别处查），
     * 一个 {@code /data/out/x.png} 在写它的进程里能打开，在别人那里只会是一个谜。</p>
     *
     * @param ref 对象引用
     * @return 问题清单（可能为空）
     */
    private static List<String> checkStorageRef(String ref) {
        List<String> problems = new ArrayList<>();
        if (ref.length() > STORAGE_REF_MAX) {
            problems.add("制品对象引用长度 " + ref.length() + " 超过 " + STORAGE_REF_MAX);
            return problems;
        }
        if (ref.matches("^[A-Za-z]:[\\\\/].*")) {
            problems.add("制品对象引用不得是 Windows 本地路径（" + ref + "）");
        } else if (ref.startsWith("/") || ref.startsWith("\\\\")) {
            problems.add("制品对象引用不得是本地绝对路径（" + ref + "），请给对象存储键");
        } else if (ref.toLowerCase(Locale.ROOT).startsWith("file:")) {
            problems.add("制品对象引用不得是 file: 协议（" + ref + "），请给对象存储键");
        } else if (ref.contains("\\")) {
            problems.add("制品对象引用请用正斜杠（对象键的形态），当前含反斜杠：" + ref);
        }
        if (containsParentSegment(ref)) {
            problems.add("制品对象引用不得含上跳路径段（..）：" + ref);
        }
        return problems;
    }

    /**
     * 对象引用里是否有 {@code ..} 路径段。
     *
     * <p>按段判断而不是用正则 {@code \.\.}：后者会把合法键里恰好含两个点的片段
     * （如 {@code v1..2/out.png} 里的 {@code v1..2}）误判成上跳，而漏判的代价又很高
     * （穿越到别的命名空间）。分段比较没有这两种歧义。</p>
     *
     * @param ref 对象引用
     * @return 含 {@code ..} 段返回 true
     */
    private static boolean containsParentSegment(String ref) {
        for (String segment : ref.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * trim，null 安全。
     *
     * @param value 原值
     * @return trim 后的值（null 输入返回空串）
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 校验结论。
     *
     * @param passed   是否通过
     * @param detail   失败明细（通过时为 null）
     * @param artifactType 归一化后的制品类型
     * @param mimeType 归一化后的 MIME（小写）
     * @param sha256   归一化后的 sha256（小写）
     * @param storageRef 归一化后的对象引用（trim）
     */
    public record AigArtifactCheck(boolean passed, String detail, String artifactType,
                                   String mimeType, String sha256, String storageRef) {
    }

}
