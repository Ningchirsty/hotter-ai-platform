package org.dromara.hrtalent.domain.vo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 人才合并预览视图对象（SPEC-P4 §2.5、设计文档 §8.18 第 2~3 步）。
 *
 * <p><b>用途</b>：合并是高风险写操作，页面必须在提交前展示「基础字段差异 + 关系数量」，
 * 由人工选择冲突字段保留值后再二次确认（§12 界面「合并前二次确认」）。</p>
 *
 * <p><b>只读</b>：本对象不落库，任何字段都不得直接作为写操作入参；
 * 写操作只接受 {@code TalentMergeBo} 的白名单冲突字段决策。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentMergePreviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 保留（主）主档摘要
     */
    private TalentProfileVo keepTalent;

    /**
     * 被合并（从）主档摘要
     */
    private TalentProfileVo mergedTalent;

    /**
     * 冲突字段差异清单（仅包含「两边都有值且不相同」的字段）
     */
    private List<FieldDiff> fieldDiffs = new ArrayList<>();

    /**
     * 关系数量清单（按关系表分类，展示迁移影响面）
     */
    private List<RelationSummary> relations = new ArrayList<>();

    /**
     * 二次确认提示语（如「合并后不可普通撤销」「不物理删除任何资料」）
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * 当前登录用户是否具备执行合并的权限（集团人才管理员/超级管理员），false 时禁止提交
     */
    private Boolean mergeAllowed;

    /**
     * 单个字段的差异项。
     *
     * @author hr-talent
     */
    @Data
    public static class FieldDiff implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 字段名（实体属性名，见 {@code TalentMergeFields}）
         */
        private String field;

        /**
         * 字段中文名
         */
        private String label;

        /**
         * 保留主档的值（字符串化，可能为 null）
         */
        private String keepValue;

        /**
         * 被合并主档的值（字符串化，可能为 null）
         */
        private String mergedValue;

        /**
         * 该字段是否允许人工选择取值
         */
        private Boolean selectable;

        /**
         * 默认取值来源（keep 保留主档 / merged 被合并主档）
         */
        private String defaultFrom;

    }

    /**
     * 单个关系表的影响面。
     *
     * @author hr-talent
     */
    @Data
    public static class RelationSummary implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 关系表名（hr_ 前缀的物理表名）
         */
        private String table;

        /**
         * 关系中文名
         */
        private String label;

        /**
         * 被合并主档当前持有的关系数量（将被转移）
         */
        private Integer mergedCount;

        /**
         * 保留主档当前持有的关系数量（转移后合计）
         */
        private Integer keepCount;

        /**
         * 迁移方式（move 改归属 / skip_conflict 冲突跳过 / follow 随上级关系转移 / pending 待接入）
         */
        private String migrateMode;

    }

}
