package org.dromara.aigov.studio.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 预检问题项（设计 §9.3：每一项包含 {@code code}、{@code fieldPath}、{@code message}、
 * {@code suggestedAction}、{@code relatedRef}）。
 *
 * <p><b>为什么不能只用一句中文</b>：预检要能<b>点击定位</b>到对应步骤/字段，并按
 * 「阻断 / 提醒」分级展示。只有一句文案时，页面既无法定位、也无法判断"这条到底拦不拦提交"，
 * 只能把所有问题都当成同等严重——结果要么把提醒也当阻断（人不敢提交），
 * 要么把阻断当提醒（提交后才在服务端被拒）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigStudioPrecheckItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 严重级：阻断
     */
    public static final String SEVERITY_BLOCKER = "BLOCKER";

    /**
     * 严重级：提醒（不拦提交）
     */
    public static final String SEVERITY_WARNING = "WARNING";

    /**
     * 问题编码（稳定词表，供前端分支与统计；如 {@code SECTION_MISSING}）
     */
    private String code;

    /**
     * 严重级（{@link #SEVERITY_BLOCKER} / {@link #SEVERITY_WARNING}）
     */
    private String severity;

    /**
     * 定位路径（如 {@code /promptSections/role}、{@code /providerCapability}）
     */
    private String fieldPath;

    /**
     * 人话说明
     */
    private String message;

    /**
     * 建议动作（人能照着做的那一步）
     */
    private String suggestedAction;

    /**
     * 关联引用（可空；如章节键、资源编码）
     */
    private String relatedRef;

    /**
     * 造一条阻断项。
     *
     * @param code        编码
     * @param fieldPath   定位路径
     * @param message     说明
     * @param suggestedAction 建议动作
     * @param relatedRef  关联引用（可空）
     * @return 问题项
     */
    public static AigStudioPrecheckItem blocker(String code, String fieldPath, String message,
                                                String suggestedAction, String relatedRef) {
        return of(code, SEVERITY_BLOCKER, fieldPath, message, suggestedAction, relatedRef);
    }

    /**
     * 造一条提醒项。
     *
     * @param code        编码
     * @param fieldPath   定位路径
     * @param message     说明
     * @param suggestedAction 建议动作
     * @param relatedRef  关联引用（可空）
     * @return 问题项
     */
    public static AigStudioPrecheckItem warning(String code, String fieldPath, String message,
                                                String suggestedAction, String relatedRef) {
        return of(code, SEVERITY_WARNING, fieldPath, message, suggestedAction, relatedRef);
    }

    private static AigStudioPrecheckItem of(String code, String severity, String fieldPath, String message,
                                            String suggestedAction, String relatedRef) {
        AigStudioPrecheckItem item = new AigStudioPrecheckItem();
        item.setCode(code);
        item.setSeverity(severity);
        item.setFieldPath(fieldPath);
        item.setMessage(message);
        item.setSuggestedAction(suggestedAction);
        item.setRelatedRef(relatedRef);
        return item;
    }

}
