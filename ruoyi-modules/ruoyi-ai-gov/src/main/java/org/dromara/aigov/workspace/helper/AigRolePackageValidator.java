package org.dromara.aigov.workspace.helper;

import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.workspace.domain.AigRolePackageDraft;
import org.dromara.aigov.workspace.enums.AigActionLaunchModeEnum;
import org.dromara.aigov.workspace.enums.AigLaunchTargetTypeEnum;
import org.dromara.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 岗位包静态校验（附件 §5.3「字段约束」；主文档线增量 1b）。
 *
 * <h3>它守的是什么</h3>
 * <p>岗位包是"给人配的"：配错了<b>不会报错</b>，只会在某天表现为"员工点了没反应"
 * 或"某个入口点进去空白"。而岗位包一旦发布，员工就是照着它用的。所以把能判定的都判定掉：
 * 引用是否解析得到、分类是否存在、跳转键是否在白名单里、数据等级有没有被"放开"。</p>
 *
 * <h3>三条刻意的取舍</h3>
 * <ol>
 *     <li><b>一次列全所有问题</b>，不抛第一个错——配置的人需要一次看到全部问题，而不是来回试。</li>
 *     <li><b>数据等级只能收紧</b>：{@code defaultDataLevel.rank <= maxDataLevel.rank}。
 *         反过来（默认等级比上限还宽）是一份自相矛盾的声明，必须当场拒绝。</li>
 *     <li><b>routeKey 一律查白名单</b>（{@link AigRouteKeyRegistry}）：不允许岗位包"自己发明"一个跳转目标。</li>
 * </ol>
 *
 * @author ai-gov
 */
public final class AigRolePackageValidator {

    /**
     * 编码形态（岗位/分类/卡片共用）：大写字母开头，允许大写字母/数字/下划线
     */
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{2,79}$");

    /**
     * 版本形态：SemVer 风格（附件 §5.3「不允许覆盖已发布制品」）
     */
    private static final Pattern VERSION = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    /**
     * 场景引用形态：{@code scenario://<code>@<version>}
     */
    private static final Pattern SCENARIO_REF =
        Pattern.compile("^scenario://([A-Za-z0-9_]+)@(\\d+\\.\\d+\\.\\d+)$");

    /**
     * 允许出现在 {@code requiredContext} 里的上下文键（附件 §8.3）。
     *
     * <p>刻意是白名单：岗位包<b>不能</b>要求平台传一个约定外的字段，
     * 更不能借它覆盖身份（那是"包定义数据权"的口子）。</p>
     */
    private static final Set<String> ALLOWED_CONTEXT_KEYS = Set.of(
        "brandId", "projectId", "productId", "skuId", "artifactId", "artifactIds",
        "taskId", "screenId", "industryCode");

    private AigRolePackageValidator() {
    }

    /**
     * 校验一份岗位包草稿。
     *
     * @param draft  草稿
     * @param lookup 外部查询（场景版本是否存在）
     * @return 问题清单（人话；通过时为空）
     */
    public static List<String> validate(AigRolePackageDraft draft, AigRolePackageLookup lookup) {
        List<String> problems = new ArrayList<>();
        if (draft == null) {
            problems.add("岗位包为空");
            return problems;
        }
        checkIdentity(draft, problems);
        Set<String> categoryCodes = checkCategories(draft, problems);
        checkActions(draft, categoryCodes, lookup, problems);
        checkPolicy(draft, problems);
        return problems;
    }

    /**
     * 岗位编码/名称/版本。
     *
     * @param draft    草稿
     * @param problems 问题清单
     */
    private static void checkIdentity(AigRolePackageDraft draft, List<String> problems) {
        if (StringUtils.isBlank(draft.roleCode())) {
            problems.add("岗位编码（roleCode）不能为空");
        } else if (!CODE.matcher(draft.roleCode().trim()).matches()) {
            problems.add("岗位编码只能是大写字母开头的字母/数字/下划线（3~80 位），实际=" + draft.roleCode());
        }
        if (StringUtils.isBlank(draft.roleName())) {
            problems.add("岗位名称（roleName）不能为空");
        }
        if (StringUtils.isBlank(draft.version())) {
            problems.add("版本号（version）不能为空");
        } else if (!VERSION.matcher(draft.version().trim()).matches()) {
            problems.add("版本号必须是 SemVer 风格（如 1.0.0），实际=" + draft.version());
        }
    }

    /**
     * 分类：至少一个、编码唯一且合法、默认分类必须在其中。
     *
     * @param draft    草稿
     * @param problems 问题清单
     * @return 合法分类编码集合
     */
    private static Set<String> checkCategories(AigRolePackageDraft draft, List<String> problems) {
        Set<String> codes = new LinkedHashSet<>();
        List<AigRolePackageDraft.CategoryDraft> categories = draft.categories() == null
            ? List.of() : draft.categories();
        if (categories.isEmpty()) {
            problems.add("至少需要一个分类（categories）");
        }
        for (AigRolePackageDraft.CategoryDraft category : categories) {
            if (category == null || StringUtils.isBlank(category.code())) {
                problems.add("分类缺少编码（category.code）");
                continue;
            }
            String code = category.code().trim();
            if (!CODE.matcher(code).matches()) {
                problems.add("分类编码只能是大写字母开头的字母/数字/下划线（3~80 位），实际=" + code);
            }
            if (!codes.add(code)) {
                problems.add("分类编码重复：" + code);
            }
            if (StringUtils.isBlank(category.name())) {
                problems.add("分类 " + code + " 缺少名称");
            }
        }
        if (StringUtils.isNotBlank(draft.defaultCategory())
            && !codes.contains(draft.defaultCategory().trim())) {
            problems.add("默认分类 " + draft.defaultCategory() + " 不在本包分类里");
        }
        return codes;
    }

    /**
     * 卡片：编码唯一、分类必须存在、启动方式/目标类型必须合法、目标引用必须解析得到、
     * routeKey 必须命中白名单、上下文键必须在白名单里。
     *
     * @param draft         草稿
     * @param categoryCodes 合法分类编码
     * @param lookup        外部查询
     * @param problems      问题清单
     */
    private static void checkActions(AigRolePackageDraft draft, Set<String> categoryCodes,
                                     AigRolePackageLookup lookup, List<String> problems) {
        List<AigRolePackageDraft.ActionDraft> actions = draft.actions() == null
            ? List.of() : draft.actions();
        if (actions.isEmpty()) {
            problems.add("至少需要一张能力卡片（actions）");
        }
        Set<String> codes = new LinkedHashSet<>();
        for (AigRolePackageDraft.ActionDraft action : actions) {
            if (action == null || StringUtils.isBlank(action.code())) {
                problems.add("能力卡片缺少编码（action.code）");
                continue;
            }
            String code = action.code().trim();
            if (!CODE.matcher(code).matches()) {
                problems.add("卡片编码只能是大写字母开头的字母/数字/下划线（3~80 位），实际=" + code);
            }
            if (!codes.add(code)) {
                problems.add("卡片编码重复：" + code);
            }
            if (StringUtils.isBlank(action.title())) {
                problems.add("卡片 " + code + " 缺少标题");
            }
            if (StringUtils.isBlank(action.categoryCode())) {
                problems.add("卡片 " + code + " 缺少分类");
            } else if (!categoryCodes.contains(action.categoryCode().trim())) {
                problems.add("卡片 " + code + " 的分类 " + action.categoryCode() + " 不在本包分类里");
            }
            AigActionLaunchModeEnum launchMode = AigActionLaunchModeEnum.find(action.launchMode());
            if (launchMode == null) {
                problems.add("卡片 " + code + " 的启动方式非法：" + action.launchMode()
                    + "（只允许 QUICK/FORM/STUDIO/NAVIGATION）");
            }
            AigLaunchTargetTypeEnum targetType = AigLaunchTargetTypeEnum.find(action.targetType());
            if (targetType == null) {
                problems.add("卡片 " + code + " 的目标类型非法：" + action.targetType()
                    + "（只允许 SCENARIO/QUICK_CAPABILITY/NAVIGATION）");
            }
            checkTarget(action, code, launchMode, targetType, lookup, problems);
            checkRequiredContext(action, code, problems);
        }
    }

    /**
     * 目标引用的解析：不同目标类型要求不同形态。
     *
     * @param action     卡片
     * @param code       卡片编码
     * @param launchMode 启动方式（可能为 null）
     * @param targetType 目标类型（可能为 null）
     * @param lookup     外部查询
     * @param problems   问题清单
     */
    private static void checkTarget(AigRolePackageDraft.ActionDraft action, String code,
                                    AigActionLaunchModeEnum launchMode,
                                    AigLaunchTargetTypeEnum targetType,
                                    AigRolePackageLookup lookup, List<String> problems) {
        if (launchMode == null || targetType == null) {
            // 启动方式/目标类型本身非法时不再叠加目标引用的报错，避免同一条配置刷出多条
            return;
        }
        String targetRef = action.targetRef() == null ? "" : action.targetRef().trim();
        if (targetType == AigLaunchTargetTypeEnum.NAVIGATION) {
            // 纯导航：目标引用本身就是 routeKey，必须在白名单里
            if (StringUtils.isBlank(targetRef)) {
                problems.add("卡片 " + code + " 是纯导航，但没给 routeKey");
            } else if (!AigRouteKeyRegistry.contains(targetRef)) {
                problems.add("卡片 " + code + " 的 routeKey " + targetRef + " 不在白名单里（不允许自己发明跳转目标）");
            }
            return;
        }
        if (StringUtils.isBlank(targetRef)) {
            problems.add("卡片 " + code + " 缺少目标引用（targetRef）");
            return;
        }
        if (targetType == AigLaunchTargetTypeEnum.SCENARIO) {
            Matcher matcher = SCENARIO_REF.matcher(targetRef);
            if (!matcher.matches()) {
                problems.add("卡片 " + code + " 的 SCENARIO 引用必须是 scenario://<code>@<版本>，实际=" + targetRef);
            } else if (lookup == null || !lookup.scenarioExists(matcher.group(1), matcher.group(2))) {
                problems.add("卡片 " + code + " 引用的场景版本不存在：" + targetRef);
            }
        } else if (AigRouteKeyRegistry.contains(targetRef)) {
            // 轻量能力不该指向一个页面——那是 NAVIGATION 的语义，混在一起会让"启动方式"失去意义
            problems.add("卡片 " + code + " 是轻量能力，但目标引用是 routeKey（" + targetRef
                + "）：页面跳转请用 NAVIGATION，别把两种语义混在同一张卡片上");
        }
        if (launchMode == AigActionLaunchModeEnum.STUDIO) {
            String routeKey = action.studioRouteKey() == null ? "" : action.studioRouteKey().trim();
            if (StringUtils.isBlank(routeKey)) {
                problems.add("卡片 " + code + " 是 STUDIO，必须给 studioRouteKey");
            } else if (!AigRouteKeyRegistry.contains(routeKey)) {
                problems.add("卡片 " + code + " 的 studioRouteKey " + routeKey + " 不在白名单里");
            }
        }
    }

    /**
     * 上下文键白名单。
     *
     * @param action   卡片
     * @param code     卡片编码
     * @param problems 问题清单
     */
    private static void checkRequiredContext(AigRolePackageDraft.ActionDraft action, String code,
                                             List<String> problems) {
        if (StringUtils.isBlank(action.requiredContext())) {
            return;
        }
        for (String raw : action.requiredContext().split(",")) {
            String key = raw.trim();
            if (key.isEmpty()) {
                continue;
            }
            if (!ALLOWED_CONTEXT_KEYS.contains(key)) {
                problems.add("卡片 " + code + " 要求的上下文键 " + key + " 不在白名单里（不允许包定义约定外的字段）");
            }
        }
    }

    /**
     * 授权策略：可见范围必填；数据等级合法且**只能收紧**。
     *
     * @param draft    草稿
     * @param problems 问题清单
     */
    private static void checkPolicy(AigRolePackageDraft draft, List<String> problems) {
        if (StringUtils.isBlank(draft.audienceScope())) {
            problems.add("可见范围（audienceScope）不能为空");
        }
        AigDataLevelEnum defaultLevel = AigDataLevelEnum.find(draft.defaultDataLevel());
        AigDataLevelEnum maxLevel = AigDataLevelEnum.find(draft.maxDataLevel());
        if (defaultLevel == null) {
            problems.add("默认数据等级非法：" + draft.defaultDataLevel());
        }
        if (maxLevel == null) {
            problems.add("最高数据等级非法：" + draft.maxDataLevel());
        }
        if (defaultLevel != null && maxLevel != null && defaultLevel.getRank() > maxLevel.getRank()) {
            problems.add("默认数据等级（" + defaultLevel.getCode() + "）比最高等级（" + maxLevel.getCode()
                + "）还宽：这是自相矛盾的声明，岗位包只能收紧、不能放宽");
        }
    }

}
