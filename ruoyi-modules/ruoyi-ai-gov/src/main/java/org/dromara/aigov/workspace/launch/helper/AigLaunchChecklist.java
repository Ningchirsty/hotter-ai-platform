package org.dromara.aigov.workspace.launch.helper;

import org.dromara.aigov.task.enums.AigTaskTypeEnum;
import org.dromara.aigov.workspace.enums.AigActionLaunchModeEnum;
import org.dromara.aigov.workspace.enums.AigLaunchTargetTypeEnum;
import org.dromara.aigov.workspace.helper.AigRouteKeyRegistry;
import org.dromara.aigov.workspace.helper.AigScenarioRef;
import org.dromara.aigov.workspace.launch.enums.AigLaunchErrorEnum;
import org.dromara.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 启动前的校验链（主文档线增量 3；附件 §12「prepare 校验链」）。
 *
 * <h3>为什么要"一次列全"</h3>
 * <p>这是员工点卡片后的即时反馈。只报第一个错会让用户来回试（填完又被告知还缺一样）。
 * 与岗位包校验器同一口径：**能一次说完就一次说完**。</p>
 *
 * <h3>为什么校验在 prepare 与 commit 各做一次</h3>
 * <p>prepare 是为了"尽早告诉用户行不行"；commit 是因为**中间隔了一段时间**——
 * 卡片可能被停用、场景版本可能被下架、配额可能耗尽。只在 prepare 校验，
 * 等于让"确认"这一步成为一个绕过所有检查的入口。</p>
 *
 * <h3>输入刻意做成一个值对象</h3>
 * <p>校验要能逐条单测（"配错了不会报错"这类风险必须靠测试拦），所以它不查库：
 * 库里的事实（场景版本是否可用、项目权、配额、运行健康）由调用方查好，作为布尔值/列表传进来。</p>
 *
 * @author ai-gov
 */
public final class AigLaunchChecklist {

    private AigLaunchChecklist() {
    }

    /**
     * 校验输入。
     *
     * @param launchMode         启动方式
     * @param targetType         目标类型
     * @param targetRef          目标引用
     * @param studioRouteKey     STUDIO 的专业页跳转键
     * @param requiredContextKeys 卡片声明的上下文键
     * @param providedContext    本次请求提供的上下文
     * @param taskType           任务类型（要建任务时必填）
     * @param projectType        业务域（要建任务时必填）
     * @param dataLevel          数据等级（要建任务时必填）
     * @param snapshotJson       输入快照（要建任务时必填）
     * @param scenarioUsable     场景版本当前是否可用（由调用方查好）
     * @param projectAccessDenied 项目权是否被拒（由调用方查好）
     * @param runtimeUnhealthy    运行时是否报告不可用（由调用方查好）
     * @author ai-gov
     */
    public record Input(
        String launchMode,
        String targetType,
        String targetRef,
        String studioRouteKey,
        List<String> requiredContextKeys,
        Map<String, String> providedContext,
        String taskType,
        String projectType,
        String dataLevel,
        String snapshotJson,
        boolean scenarioUsable,
        boolean projectAccessDenied,
        boolean runtimeUnhealthy) {

        /**
         * 该启动方式是否会产生平台任务。
         *
         * <p>NAVIGATION 只是打开页面，不存在"任务"；其余启动方式都要落到任务上，
         * 因此必须能把任务描述清楚（任务类型/业务域/数据等级/输入快照）。</p>
         *
         * @return 会建任务返回 true
         */
        public boolean requiresTask() {
            return AigActionLaunchModeEnum.find(launchMode) != AigActionLaunchModeEnum.NAVIGATION;
        }
    }

    /**
     * 跑一遍校验链。
     *
     * @param input 输入
     * @return 问题清单（按"先看得到、再改得动、最后才是资源"的顺序；通过时为空）
     */
    public static List<AigLaunchErrorEnum> check(Input input) {
        List<AigLaunchErrorEnum> problems = new ArrayList<>();
        if (input == null) {
            problems.add(AigLaunchErrorEnum.ACTION_NOT_AVAILABLE);
            return problems;
        }
        AigActionLaunchModeEnum mode = AigActionLaunchModeEnum.find(input.launchMode());
        AigLaunchTargetTypeEnum targetType = AigLaunchTargetTypeEnum.find(input.targetType());
        if (mode == null || targetType == null) {
            // 卡片本身读不懂：这不是用户能改的东西
            problems.add(AigLaunchErrorEnum.ACTION_NOT_AVAILABLE);
            return problems;
        }
        checkTarget(input, mode, targetType, problems);
        checkContext(input, problems);
        checkTaskInput(input, problems);
        // 资源类问题放最后：先说"这张卡片能不能用"，再说"你的额度/运行时"
        if (input.projectAccessDenied()) {
            problems.add(AigLaunchErrorEnum.PROJECT_ACCESS_DENIED);
        }
        if (input.runtimeUnhealthy()) {
            problems.add(AigLaunchErrorEnum.RUNTIME_UNHEALTHY);
        }
        return problems;
    }

    /**
     * 目标解析：导航必须命中白名单；场景引用必须解析得出且当前可用。
     *
     * @param input     输入
     * @param mode      启动方式
     * @param targetType 目标类型
     * @param problems  问题清单
     */
    private static void checkTarget(Input input, AigActionLaunchModeEnum mode,
                                    AigLaunchTargetTypeEnum targetType, List<AigLaunchErrorEnum> problems) {
        String targetRef = input.targetRef() == null ? "" : input.targetRef().trim();
        if (targetType == AigLaunchTargetTypeEnum.NAVIGATION) {
            if (!AigRouteKeyRegistry.contains(targetRef)) {
                // 不在白名单 = 这个跳转目标平台不认识（发布时已拦过，这里再拦是防"发布后被改库"）
                problems.add(AigLaunchErrorEnum.ACTION_NOT_AVAILABLE);
            }
            return;
        }
        if (targetType == AigLaunchTargetTypeEnum.SCENARIO) {
            AigScenarioRef ref = AigScenarioRef.parse(targetRef);
            if (ref == null || !input.scenarioUsable()) {
                problems.add(AigLaunchErrorEnum.SCENE_VERSION_BLOCKED);
            }
            return;
        }
        // 轻量能力：目标引用就是能力编码，空编码等于没有目标
        if (StringUtils.isBlank(targetRef)) {
            problems.add(AigLaunchErrorEnum.ACTION_NOT_AVAILABLE);
        }
        if (mode == AigActionLaunchModeEnum.STUDIO && !AigRouteKeyRegistry.contains(trim(input.studioRouteKey()))) {
            problems.add(AigLaunchErrorEnum.ACTION_NOT_AVAILABLE);
        }
    }

    /**
     * 上下文键：卡片声明要什么，请求就得给什么。
     *
     * @param input    输入
     * @param problems 问题清单
     */
    private static void checkContext(Input input, List<AigLaunchErrorEnum> problems) {
        Set<String> provided = new LinkedHashSet<>();
        if (input.providedContext() != null) {
            for (Map.Entry<String, String> entry : input.providedContext().entrySet()) {
                if (StringUtils.isNotBlank(entry.getValue())) {
                    provided.add(entry.getKey());
                }
            }
        }
        if (input.requiredContextKeys() == null) {
            return;
        }
        for (String key : input.requiredContextKeys()) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            if (!provided.contains(key.trim())) {
                problems.add(AigLaunchErrorEnum.REQUIRED_INPUT_MISSING);
                return;
            }
        }
    }

    /**
     * 要建任务的启动方式必须能把任务描述清楚。
     *
     * <p>{@code taskType} 必须是封闭集合里的取值：拿一个平台不认识的类型去建任务，
     * 表现是"任务建出来了但永远不动"——比当场拒绝难查得多。</p>
     *
     * @param input    输入
     * @param problems 问题清单
     */
    private static void checkTaskInput(Input input, List<AigLaunchErrorEnum> problems) {
        if (!input.requiresTask()) {
            return;
        }
        if (AigTaskTypeEnum.find(input.taskType()) == null
            || StringUtils.isBlank(input.projectType())
            || StringUtils.isBlank(input.dataLevel())
            || StringUtils.isBlank(input.snapshotJson())) {
            problems.add(AigLaunchErrorEnum.REQUIRED_INPUT_MISSING);
        }
    }

    /**
     * null 安全 trim。
     *
     * @param value 值
     * @return 结果
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 场景版本此刻能否被启动引用。
     *
     * <p><b>只有 {@code STABLE} 算可用</b>，而岗位包**发布**时只要求"引用存在"。
     * 两者刻意不同：发布时场景可能还在验证（引用是对的，只是还不能跑），
     * 而员工真正点下去的时候，场景必须是已经就绪的那一版。
     * 若这里放宽成"任意状态"，表现会是"卡片能点、任务起了但场景没准备好"。</p>
     *
     * <p>取值沿用既有发布状态机（F-10 不另立一套），因此这里是一个**封闭集合**：
     * 新增状态必须显式决定它算不算"可用"。</p>
     *
     * @param releaseStatus 场景版本发布状态
     * @return 可用返回 true
     */
    public static boolean scenarioUsable(String releaseStatus) {
        return releaseStatus != null && "STABLE".equalsIgnoreCase(releaseStatus.trim());
    }

}
