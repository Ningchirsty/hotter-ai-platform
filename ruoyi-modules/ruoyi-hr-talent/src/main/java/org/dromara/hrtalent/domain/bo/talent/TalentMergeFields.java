package org.dromara.hrtalent.domain.bo.talent;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 人才合并可人工选择的冲突字段白名单（SPEC-P4 §2.5、设计文档 §8.18）。
 *
 * <p><b>安全边界</b>：只有本白名单内的字段允许在合并时人工选择取值；
 * 联系方式密文/哈希、人才编号、归属公司部门、可见范围、数据分级、乐观锁版本与
 * 被合并标记（{@code merged_to_id} / {@code talent_status}）<b>一律不允许</b>通过合并接口改写。</p>
 *
 * <p>字段名使用实体属性名（camelCase），与 {@link TalentMergeBo.FieldDecision#getField()} 一致。</p>
 *
 * @author hr-talent
 */
public final class TalentMergeFields {

    /**
     * 姓名。
     */
    public static final String NAME = "name";

    /**
     * 性别。
     */
    public static final String GENDER = "gender";

    /**
     * 最高学历。
     */
    public static final String HIGHEST_EDUCATION = "highestEducation";

    /**
     * 当前所在城市。
     */
    public static final String CURRENT_CITY = "currentCity";

    /**
     * 期望工作城市。
     */
    public static final String EXPECTED_CITY = "expectedCity";

    /**
     * 当前公司。
     */
    public static final String CURRENT_COMPANY = "currentCompany";

    /**
     * 当前职位。
     */
    public static final String CURRENT_POSITION = "currentPosition";

    /**
     * 期望岗位。
     */
    public static final String EXPECTED_POSITION = "expectedPosition";

    /**
     * 所属行业。
     */
    public static final String INDUSTRY = "industry";

    /**
     * 工作年限。
     */
    public static final String WORK_YEARS = "workYears";

    /**
     * 允许人工选择的冲突字段集合（不可变）。
     */
    public static final Set<String> ALLOWED = Set.of(
        NAME, GENDER, HIGHEST_EDUCATION, CURRENT_CITY, EXPECTED_CITY,
        CURRENT_COMPANY, CURRENT_POSITION, EXPECTED_POSITION, INDUSTRY, WORK_YEARS);

    /**
     * 字段名 → 中文名（仅用于页面提示与日志，不参与判定）。
     */
    private static final java.util.Map<String, String> LABELS = buildLabels();

    /**
     * 工具类禁止实例化。
     */
    private TalentMergeFields() {
    }

    /**
     * 判断字段是否允许人工选择取值。
     *
     * @param field 字段名
     * @return 是否允许
     */
    public static boolean allowed(String field) {
        return field != null && ALLOWED.contains(field);
    }

    /**
     * 取字段中文名。
     *
     * @param field 字段名
     * @return 中文名；未知字段返回字段名本身
     */
    public static String label(String field) {
        return LABELS.getOrDefault(field, field);
    }

    /**
     * 构造字段中文名映射。
     *
     * @return 字段中文名映射
     */
    private static java.util.Map<String, String> buildLabels() {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put(NAME, "姓名");
        map.put(GENDER, "性别");
        map.put(HIGHEST_EDUCATION, "最高学历");
        map.put(CURRENT_CITY, "当前所在城市");
        map.put(EXPECTED_CITY, "期望工作城市");
        map.put(CURRENT_COMPANY, "当前公司");
        map.put(CURRENT_POSITION, "当前职位");
        map.put(EXPECTED_POSITION, "期望岗位");
        map.put(INDUSTRY, "所属行业");
        map.put(WORK_YEARS, "工作年限");
        return java.util.Collections.unmodifiableMap(map);
    }

    /**
     * 可人工选择的字段名集合副本（便于测试断言顺序稳定）。
     *
     * @return 字段名有序集合
     */
    public static Set<String> orderedFields() {
        return new LinkedHashSet<>(LABELS.keySet());
    }

}
