package org.dromara.hrtalent.domainservice;

/**
 * 人才合并关系迁移领域服务（SPEC-P4 §2.5、设计文档 §8.18、§21.7）。
 *
 * <p><b>职责</b>：把「被合并主档」持有的全部关系改归属到「保留主档」，并提供迁移前后数量快照。
 * 本服务<b>只做改归属与统计</b>：</p>
 * <ul>
 *     <li><b>绝不物理删除</b>任何资料（本接口不提供任何删除能力）；</li>
 *     <li>不处理主档字段合并、不写合并日志、不判断权限 —— 这些属于
 *     {@code ITalentDuplicateService#merge} 的编排职责。</li>
 * </ul>
 *
 * <p><b>事务</b>：实现类不自行开启事务，由调用方（合并服务）在同一个 {@code @Transactional}
 * 方法内调用，保证「关系转移 + 主档标记 + 合并快照」原子提交（§21.7）。</p>
 *
 * <p><b>测试可见性</b>：本接口是合并编排唯一的跨表写入口；
 * 单元测试用替身实现统计 {@code move*} 调用次数，即可在不接触数据库的前提下断言
 * 「确实发生了关系转移、且没有发生物理删除」。</p>
 *
 * @author hr-talent
 */
public interface ITalentMergeRelationMigrator {

    /**
     * 统计指定主档当前持有的全部关系数量（迁移前/迁移后各调用一次）。
     *
     * @param talentId 人才主档ID
     * @return 关系数量快照（不为 null）
     */
    TalentMergeRelationSnapshot snapshot(Long talentId);

    /**
     * 在<b>同一事务</b>内把被合并主档的关系全部改归属到保留主档。
     *
     * <p>调用顺序（顺序有业务含义，不可随意调整）：</p>
     * <ol>
     *     <li>简历版本 → 教育/工作/项目经历 → 应聘记录（先移简历，再移引用简历的经历）；</li>
     *     <li>人才池成员、标签、跟进、分组、共享授权、人才级附件、关键字段变更历史；</li>
     *     <li>挂在应聘记录上的附件随应聘记录改归属自动跟随，无需单独处理。</li>
     * </ol>
     *
     * <p><b>审计字段</b>：改归属使用自定义 SQL，不经过 MyBatis-Plus 的自动填充，
     * 因此 {@code operatorId} 会被<b>显式</b>写入每张关系表的 {@code update_by}
     * 与 {@code update_time}，保证「谁在何时把关系改到保留主档」可追溯。</p>
     *
     * @param keepTalentId   保留（主）人才主档ID
     * @param mergedTalentId 被合并（从）人才主档ID
     * @param operatorId     操作人用户ID（写入关系表审计字段）
     * @return 本次迁移实际影响的行数（关系表 → 行数）
     */
    java.util.Map<String, Long> migrate(Long keepTalentId, Long mergedTalentId, Long operatorId);

}
