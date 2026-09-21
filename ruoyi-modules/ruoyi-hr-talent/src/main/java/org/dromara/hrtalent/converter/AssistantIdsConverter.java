package org.dromara.hrtalent.converter;

import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 岗位协助人用户ID与入库字符串之间的类型转换器。
 *
 * <p>数据库列 {@code hr_recruit_job.assistant_ids} 是 {@code varchar(255)} 的英文逗号分隔用户ID串，
 * 而接口契约要求 BO/VO 用 {@code Long[] assistantIds} 数组（前端多选组件直接绑定）。
 * 这里提供一对方法供 {@code @AutoMapper(uses = ...)} 使用，避免在两个方向上手写转换、
 * 也避免 MapStruct 因同名不同类型的属性报错。</p>
 *
 * <p>转换只做格式归一：去空、去重、保持首次出现顺序；不查询数据库、不校验用户是否存在。
 * 注册为 Spring Bean 是因为 mapstruct-plus 生成代码通过容器获取 {@code uses} 类实例。</p>
 *
 * @author hr-talent
 */
@Component
public class AssistantIdsConverter {

    /**
     * 分隔符：英文逗号（与 DDL 注释一致）。
     */
    private static final String SEPARATOR = ",";

    /**
     * 逗号分隔的用户ID串转换为用户ID数组。
     *
     * @param assistantIdText 入库原串，可为 null 或空
     * @return 用户ID数组，无有效元素时返回 null
     */
    public Long[] toAssistantIds(String assistantIdText) {
        if (StringUtils.isBlank(assistantIdText)) {
            return null;
        }
        String[] parts = StringUtils.split(assistantIdText, ',');
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String part : parts) {
            String trimmed = part == null ? null : part.trim();
            if (StringUtils.isBlank(trimmed)) {
                continue;
            }
            try {
                ids.add(Long.valueOf(trimmed));
            } catch (NumberFormatException e) {
                // 历史脏数据不阻断查询，跳过非法分段
            }
        }
        return ids.isEmpty() ? null : ids.toArray(new Long[0]);
    }

    /**
     * 用户ID数组转换为逗号分隔的入库串。
     *
     * @param assistantIds 用户ID数组，可为 null
     * @return 逗号分隔串，无有效元素时返回 null
     */
    public String toAssistantIdText(Long[] assistantIds) {
        if (assistantIds == null || assistantIds.length == 0) {
            return null;
        }
        String text = Arrays.stream(assistantIds)
            .filter(Objects::nonNull)
            .distinct()
            .map(String::valueOf)
            .collect(Collectors.joining(SEPARATOR));
        return StringUtils.isBlank(text) ? null : text;
    }

}
