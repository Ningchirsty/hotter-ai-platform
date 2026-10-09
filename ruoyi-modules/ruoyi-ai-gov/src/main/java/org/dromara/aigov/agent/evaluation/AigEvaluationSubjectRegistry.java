package org.dromara.aigov.agent.evaluation;

import org.dromara.common.core.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 评测执行器注册表（按「评测对象」派发）。
 *
 * <p><b>为什么用 setter 注入而不是构造器注入</b>：{@code List<X>} 作为构造器参数时，如果当前
 * 上下文里一个 {@code X} 都没有，Spring 会把它判成「找不到依赖」并让<b>整个应用启动失败</b>。
 * 而本模块必须先能独立启动——评测执行器由业务模块按需注册，在业务实现落位之前
 * （本步只落平台侧机制）一个实现都不存在。因此这里用 {@code @Autowired(required=false)}
 * 的 setter：没有实现就是空注册表，派发时给出「平台认识哪些对象」的可读错误，
 * 而不是让治理模块起不来。同时也让单测可以直接 {@code new} 出来塞假执行器。</p>
 *
 * <p>同一评测对象注册了多个执行器时<b>报错</b>，不挑第一个：挑第一个意味着其中一家的行为
 * 被悄悄忽略，而「评测结果来自哪个实现」是结论可信度的前提。</p>
 *
 * @author ai-gov
 */
@Component
public class AigEvaluationSubjectRegistry {

    /**
     * 已注册的执行器（默认空）
     */
    private List<IAigEvaluationSubject> subjects = List.of();

    /**
     * 收集容器里全部评测执行器。
     *
     * @param subjects 执行器清单（可为 null = 一个都没有）
     */
    @Autowired(required = false)
    public void setSubjects(List<IAigEvaluationSubject> subjects) {
        this.subjects = subjects == null ? List.of() : List.copyOf(subjects);
    }

    /**
     * 按评测对象查找执行器。
     *
     * @param targetType  评测对象类型
     * @param subjectCode 评测对象编码
     * @return 执行器
     * @throws org.dromara.common.core.exception.ServiceException 没注册或多头注册时
     */
    public IAigEvaluationSubject find(String targetType, String subjectCode) {
        List<IAigEvaluationSubject> matched = new ArrayList<>();
        for (IAigEvaluationSubject subject : subjects) {
            if (subject.supports(targetType, subjectCode)) {
                matched.add(subject);
            }
        }
        if (matched.isEmpty()) {
            throw new org.dromara.common.core.exception.ServiceException(
                "没有为评测对象 " + targetType + ":" + subjectCode + " 注册评测执行器，无法跑评测。"
                    + "已注册：" + describeAll());
        }
        if (matched.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (IAigEvaluationSubject subject : matched) {
                if (sb.length() > 0) {
                    sb.append('、');
                }
                sb.append(subject.getClass().getSimpleName()).append('(').append(subject.describe())
                    .append(')');
            }
            throw new org.dromara.common.core.exception.ServiceException(
                "同一评测对象 " + targetType + ":" + subjectCode + " 注册了多个评测执行器：" + sb
                    + "。评测结论必须能追溯到一个确定的实现，请去掉多余的注册");
        }
        return matched.get(0);
    }

    /**
     * 是否存在<b>唯一</b>的评测执行器（不抛异常，供「该不该走人工录入」判断）。
     *
     * <p>与 {@link #find} 的区别：这个方法是<b>探针</b>，用来回答"平台能不能自己跑"，
     * 而不是"给我那个执行器"。因此：</p>
     * <ul>
     *     <li>0 个 → false：平台没有该对象的执行器，人工录入是唯一可取到证据的途径；</li>
     *     <li>1 个 → true：平台能自己跑，人工录入不该被用来绕过判据；</li>
     *     <li>多个 → false：那是重复注册的配置错误（{@link #find} 会拒绝执行）。
     *         这里刻意<b>不</b>当成"有执行器"——否则重复注册会把对象锁死在
     *         「机器评测跑不了、人工录入也不许」的死角里，而配置错误应当由报错暴露，
     *         不该变成一道没人能过的门。</li>
     * </ul>
     *
     * @param targetType  评测对象类型
     * @param subjectCode 评测对象编码
     * @return 恰好一个执行器返回 true
     */
    public boolean hasSingleExecutor(String targetType, String subjectCode) {
        int matched = 0;
        for (IAigEvaluationSubject subject : subjects) {
            if (subject.supports(targetType, subjectCode)) {
                matched++;
            }
        }
        return matched == 1;
    }

    /**
     * 已注册执行器的自述清单（报错时附上）。
     *
     * @return 可读清单；一个都没有时返回「（空）」
     */
    public String describeAll() {
        if (subjects.isEmpty()) {
            return "（空：还没有任何评测执行器注册到平台）";
        }
        List<String> described = new ArrayList<>();
        for (IAigEvaluationSubject subject : subjects) {
            described.add(subject.describe());
        }
        return StringUtils.join(described, "、");
    }

    /**
     * 已注册数量（供页面/自检显示）。
     *
     * @return 数量
     */
    public int size() {
        return subjects.size();
    }

}
