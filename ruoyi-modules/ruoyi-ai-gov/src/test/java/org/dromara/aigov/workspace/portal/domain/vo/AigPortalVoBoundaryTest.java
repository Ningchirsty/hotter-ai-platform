package org.dromara.aigov.workspace.portal.domain.vo;

import org.dromara.aigov.workspace.recommend.domain.vo.AigRecommendResultVo;
import org.dromara.aigov.workspace.recommend.domain.vo.AigRecommendSuggestionVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 门户对外契约的边界测试（增量 2）。
 *
 * <p>门户是<b>对全体员工</b>的接口。向它"顺手多加一个字段"不会报错、不会被编译期拦住，
 * 只会在某天把内部细节发给所有员工。所以用一条轻量守卫把边界写成断言：
 * 计费、供应商、traceId、策略原因、错误详情这些<b>运维视角</b>的字段不允许出现在门户 VO 上。</p>
 *
 * <p>反过来也断言门户确实带上了它需要的字段——免得为了"安全"把该给的信息也一起删了，
 * 让员工看不到任务状态。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigPortalVoBoundaryTest {

    /**
     * 明确不允许出现在门户 VO 上的字段（内部/运维视角）
     */
    private static final Set<String> FORBIDDEN = Set.of(
        "costAmount", "traceId", "providerCode", "providerJobId", "policyResult", "policyReason",
        "errorCode", "errorMessage", "idempotencyKey", "inputSnapshotId", "agentVersionId",
        "allowExternal", "reviewComment", "reviewedBy", "reviewedAt", "remark",
        "manifestJson", "manifestSha256", "actionId", "enabled",
        // 产物台账里的内部事实：对象存储键与完整性证据不该进入对外契约（增量 6）
        "storageRef", "sha256", "hashVerified", "validationDetail", "resultId", "attemptNo",
        "validationStatus");

    /**
     * 门户确实要带的字段
     */
    private static final Set<String> REQUIRED_ON_TASK = Set.of(
        "taskId", "taskNo", "taskType", "status", "statusLabel", "progress", "dataLevel",
        "scenarioCode", "projectType", "projectId", "startedAt", "finishedAt");

    /**
     * 产物的边界：要带"这是什么、多大、属于哪个任务"，**不要**带下载直链与存储键
     */
    private static final Set<String> REQUIRED_ON_ARTIFACT = Set.of(
        "artifactId", "taskId", "taskNo", "artifactType", "mimeType", "sizeBytes", "createTime");

    @Test
    @DisplayName("门户 VO 不允许出现计费/供应商/trace/策略/存储键/完整性证据等内部字段")
    void portalViewKeepsInternalFieldsOut() {
        assertNoneOf(AigPortalTaskVo.class, FORBIDDEN);
        assertNoneOf(AigPortalActionVo.class, FORBIDDEN);
        assertNoneOf(AigPortalRoleVo.class, FORBIDDEN);
        assertNoneOf(AigPortalRoleHomeVo.class, FORBIDDEN);
        assertNoneOf(AigPortalCategoryVo.class, FORBIDDEN);
        assertNoneOf(AigPortalArtifactVo.class, FORBIDDEN);
        // 增量 7：推荐结果同样是对员工下发的，审计/traceId/模型这些运维视角一并禁掉
        assertNoneOf(AigRecommendResultVo.class, FORBIDDEN);
        assertNoneOf(AigRecommendSuggestionVo.class, FORBIDDEN);
    }

    @Test
    @DisplayName("推荐结果带岗位归属与卡片本体，但不带模型/traceId（审计由网关写，不是这个接口）")
    void recommendViewKeepsCardContextOut() {
        Set<String> names = fieldNames(AigRecommendSuggestionVo.class);
        Set<String> missing = new LinkedHashSet<>(Set.of("roleCode", "roleName", "action"));
        missing.removeAll(names);
        assertTrue(missing.isEmpty(), "推荐结果缺少卡片归属/本体：" + missing);
    }

    @Test
    @DisplayName("门户任务视图要带任务状态与归属等员工需要的信息")
    void portalTaskViewKeepsWhatEmployeesNeed() {
        Set<String> names = fieldNames(AigPortalTaskVo.class);
        Set<String> missing = new LinkedHashSet<>(REQUIRED_ON_TASK);
        missing.removeAll(names);
        assertTrue(missing.isEmpty(), "门户任务视图缺少员工需要的字段：" + missing);
    }

    @Test
    @DisplayName("门户产物视图带来源任务与基本元数据，但不给下载直链（下载仍走专业台/任务域）")
    void portalArtifactViewKeepsMetadataOnly() {
        Set<String> names = fieldNames(AigPortalArtifactVo.class);
        Set<String> missing = new LinkedHashSet<>(REQUIRED_ON_ARTIFACT);
        missing.removeAll(names);
        assertTrue(missing.isEmpty(), "门户产物视图缺少员工需要的字段：" + missing);
        // storageRef 被禁是刻意的：给了它等于把存储布局写进对外契约；
        // 也不提供 downloadUrl —— 那会绕过专业台/任务域自己的权限
        assertTrue(!names.contains("downloadUrl"), "门户产物视图不应提供下载直链：" + names);
    }

    /**
     * 断言某类没有这些字段。
     *
     * @param type    类型
     * @param banned  禁止出现的字段名
     */
    private static void assertNoneOf(Class<?> type, Set<String> banned) {
        Set<String> names = fieldNames(type);
        Set<String> hit = new LinkedHashSet<>();
        for (String name : banned) {
            if (names.contains(name)) {
                hit.add(name);
            }
        }
        assertTrue(hit.isEmpty(), type.getSimpleName() + " 出现了不该对员工暴露的字段：" + hit
            + "（门户是对全体员工的接口：多一个字段不会报错，只会把内部细节发出去）");
    }

    /**
     * 读字段名（跳过序列化常量）。
     *
     * @param type 类型
     * @return 字段名集合
     */
    private static Set<String> fieldNames(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        for (Field field : type.getDeclaredFields()) {
            if ("serialVersionUID".equals(field.getName())) {
                continue;
            }
            names.add(field.getName());
        }
        // 继承来的字段也算（详情 VO 继承列表 VO）
        for (Field field : type.getSuperclass() == null ? new Field[0] : type.getSuperclass().getDeclaredFields()) {
            if (!"serialVersionUID".equals(field.getName())) {
                names.add(field.getName());
            }
        }
        return names;
    }

}
