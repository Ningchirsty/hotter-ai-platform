package org.dromara.hrtalent.domainservice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 人才合并关系迁移快照（SPEC-P4 §2.5、设计文档 §8.18）。
 *
 * <p>合并前后各取一次：{@link Kind#BEFORE} 记录被合并主档当前持有的关系数量，
 * {@link Kind#AFTER} 记录合并后被合并主档残留的关系数量（正常应全部为 0）。
 * 两份快照合并写入 {@code hr_talent_merge_log.relation_count_json}，
 * 是事后核对「关系是否完整转移」的唯一依据。</p>
 *
 * <p><b>如何读出「跳过」语义</b>：合并快照同时保存 {@code before}（本快照）、
 * {@code moved}（实际改归属行数）与 {@code after}（迁移后残留）。
 * 三者关系为：{@code before = moved + skipped}，且正常情况下 {@code after = 0}。
 * 因此</p>
 * <ul>
 *     <li>{@code before − moved > 0} 表示存在<b>冲突跳过</b>的关系行
 *     （{@code hr_talent_pool_member} / {@code hr_talent_profile_tag} / {@code hr_talent_group_member}
 *     的唯一键冲突：保留主档已在同一人才池/分组/拥有同一标签，保留主档已有关系优先，
 *     <b>冲突行不迁移、不删除、不覆盖</b>）；</li>
 *     <li>{@code after ≠ 0} 表示有未被任何语句覆盖的残留关系，属异常，需要人工核查。</li>
 * </ul>
 *
 * @param kind    快照时点（before 迁移前 / after 迁移后）
 * @param counts  关系表 → 数量（表名使用物理表名 {@code hr_*}）
 * @author hr-talent
 */
public record TalentMergeRelationSnapshot(Kind kind, Map<String, Long> counts) {

    /**
     * 快照时点。
     *
     * @author hr-talent
     */
    public enum Kind {

        /**
         * 迁移前。
         */
        BEFORE("before"),

        /**
         * 迁移后。
         */
        AFTER("after");

        /**
         * 时点编码（写入快照 JSON）。
         */
        private final String code;

        /**
         * 构造时点枚举。
         *
         * @param code 时点编码
         */
        Kind(String code) {
            this.code = code;
        }

        /**
         * 取时点编码。
         *
         * @return 时点编码
         */
        public String getCode() {
            return code;
        }

    }

    /**
     * 紧凑构造器：保证 {@link #counts()} 不为 null 且可安全遍历。
     */
    public TalentMergeRelationSnapshot {
        counts = counts == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(counts));
    }

    /**
     * 取指定关系表的数量。
     *
     * @param table 物理表名
     * @return 数量；不存在返回 0
     */
    public long count(String table) {
        return counts.getOrDefault(table, 0L);
    }

}
