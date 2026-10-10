package org.dromara.aigov.workspace.helper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.dromara.aigov.workspace.domain.AigRolePackageDraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位包清单（{@code aig_role_version.manifest_json}）的<b>组装与还原</b>（主文档线增量 1b）。
 *
 * <h3>为什么清单里放"分类 + 策略"，却<b>不</b>放卡片</h3>
 * <p>与数据层那张表的取舍是同一条：卡片要"按分类查 / 排序 / 单独启停"，所以拆成
 * {@code aig_role_action} 行的形式；分类数量少、总与版本整份同读，留在清单里即可。
 * 清单还要承载 policy（数据等级只能收紧）与 presentation（可见范围）——
 * 它们不是"一行一个"的对象，塞进表里只会多出一张"每版本只有一行"的表。</p>
 *
 * <h3>为什么必须有 {@link #fromJson}（还原）</h3>
 * <p>校验器 {@link AigRolePackageValidator} 的输入是 {@link AigRolePackageDraft}。
 * 要校验<b>库里已存在</b>的那份版本（发布前必做），就得把它还原成草稿。
 * <b>还原一旦缺字段，校验会静默变松</b>：比如 categories 读不出来，
 * "卡片分类不在本包"就永远不报错——这正是那种"配错了不报错、只在某天表现为员工点了没反应"
 * 的缺陷。所以还原逻辑有专门用例（分类/策略/缺字段/非法 JSON）。</p>
 *
 * <h3>入库的文本是<b>规范化</b>的</h3>
 * <p>{@link #toJson} 返回的已经是规范化 JSON（键排序、无多余空白），因此
 * {@code manifest_sha256 = hash(manifest_json)} 恒成立；反过来，任何人手工改了库里的文本，
 * 重算哈希就对不上——"已发布版本的清单有没有被改过"因此是一个可判定的问题。</p>
 *
 * @author ai-gov
 */
public final class AigRolePackageManifest {

    /**
     * 清单不是草稿内容（不参与训练台那套哈希口径），这里只做读写
     */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * 清单格式版本：将来清单结构若要改，靠它区分"老版本怎么读"
     */
    private static final int SCHEMA_VERSION = 1;

    private AigRolePackageManifest() {
    }

    /**
     * 把草稿的"非卡片部分"组装成规范化清单 JSON。
     *
     * @param draft 草稿（卡片会被忽略：它们落在 {@code aig_role_action} 表里）
     * @return 规范化 JSON
     * @throws IllegalArgumentException 草稿为空
     */
    public static String toJson(AigRolePackageDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("岗位包草稿不能为空：无法生成清单");
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("roleCode", draft.roleCode());
        root.put("roleName", draft.roleName());
        root.put("version", draft.version());
        List<Map<String, Object>> categories = new ArrayList<>();
        if (draft.categories() != null) {
            for (AigRolePackageDraft.CategoryDraft category : draft.categories()) {
                if (category == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", category.code());
                item.put("name", category.name());
                categories.add(item);
            }
        }
        root.put("categories", categories);
        root.put("defaultCategory", draft.defaultCategory());
        Map<String, Object> presentation = new LinkedHashMap<>();
        presentation.put("audienceScope", draft.audienceScope());
        root.put("presentation", presentation);
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("defaultDataLevel", draft.defaultDataLevel());
        policy.put("maxDataLevel", draft.maxDataLevel());
        root.put("policy", policy);
        // 入库即规范化：这样 manifest_sha256 就是这份文本本身的哈希，改一个空格就会被发现
        return AigRoleManifestHasher.canonicalize(MAPPER.writeValueAsString(root));
    }

    /**
     * 从清单 JSON + 卡片行还原出可校验的草稿。
     *
     * <p>缺失字段一律还原成 {@code null}（而不是抛异常）：让校验器把
     * "缺了什么"作为问题报出来，比在这里抛一个技术栈异常更有用。</p>
     *
     * @param manifestJson 清单 JSON（可为空）
     * @param actions      卡片（可为空）
     * @return 草稿
     * @throws IllegalArgumentException 清单不是合法 JSON
     */
    public static AigRolePackageDraft fromJson(String manifestJson, List<AigRolePackageDraft.ActionDraft> actions) {
        List<AigRolePackageDraft.ActionDraft> safeActions = actions == null ? List.of() : actions;
        if (manifestJson == null || manifestJson.isBlank()) {
            return new AigRolePackageDraft(null, null, null, List.of(), null, safeActions, null, null, null);
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(manifestJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("岗位包清单不是合法 JSON：" + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("岗位包清单必须是 JSON 对象");
        }
        List<AigRolePackageDraft.CategoryDraft> categories = new ArrayList<>();
        JsonNode categoryNodes = root.get("categories");
        if (categoryNodes != null && categoryNodes.isArray()) {
            for (JsonNode item : categoryNodes) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                categories.add(new AigRolePackageDraft.CategoryDraft(
                    text(item, "code"), text(item, "name")));
            }
        }
        JsonNode presentation = root.get("presentation");
        JsonNode policy = root.get("policy");
        return new AigRolePackageDraft(
            text(root, "roleCode"),
            text(root, "roleName"),
            text(root, "version"),
            categories,
            text(root, "defaultCategory"),
            safeActions,
            text(presentation, "audienceScope"),
            text(policy, "defaultDataLevel"),
            text(policy, "maxDataLevel"));
    }

    /**
     * 读一个字符串字段（缺失/null 返回 null，不返回空串——空串会被校验当成"给了一个非法值"）。
     *
     * @param node  对象节点（可空）
     * @param field 字段名
     * @return 字符串或 null
     */
    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isObject() || value.isArray()) {
            return null;
        }
        return value.asText();
    }

}
