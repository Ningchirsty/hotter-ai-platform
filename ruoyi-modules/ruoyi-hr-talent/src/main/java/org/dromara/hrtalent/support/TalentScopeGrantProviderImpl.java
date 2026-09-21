package org.dromara.hrtalent.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.entity.TalentScopeGrant;
import org.dromara.hrtalent.enums.TalentPermissionLevelEnum;
import org.dromara.hrtalent.mapper.TalentScopeGrantMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@link TalentScopeGrantProvider} 的落地实现（SPEC-P4 §2.4，设计文档 §21.14）。
 *
 * <p><b>这是 P1 留下的扩展点的真正落地</b>：在本次实现接入之前，
 * {@code TalentScopeDomainService} 内的 {@code ObjectProvider<TalentScopeGrantProvider>}
 * 永远取不到实现，{@code hasGrant(...)} 恒返回 {@code false}，因此：</p>
 * <ul>
 *     <li>{@code TalentVisibilityTypeEnum#EXPLICIT}（「显式授权」可见范围）分支<b>从未生效</b>，
 *     只能看到集团共享 / 归属公司 / 归属部门 / 本人负责的人才，</li>
 *     <li>{@code TalentScopeDomainService#checkPermissionLevel} 对非超管、非集团级管理员
 *     <b>永远判定为授权级别不足</b>（summary / detail / attachment 全部拒绝）。</li>
 * </ul>
 * <p>本类以 {@code @Component} 注册后，上述两条能力（P3 已写好的代码路径）首次真正生效。</p>
 *
 * <p><b>命中条件</b>（与 {@code TalentScopeDomainService#visibleTalentWrapper()} 内的
 * {@code EXISTS} 子查询逐条对齐，设计文档 §11.1「授权过期立即失效」）：</p>
 * <ol>
 *     <li>{@code talent_id} 匹配；</li>
 *     <li>{@code del_flag = '0'}；</li>
 *     <li>{@code revoke_flag = '0'}（已撤销不再参与判定）；</li>
 *     <li>{@code valid_from <= now}（未生效不算命中）；</li>
 *     <li>{@code valid_to} 为空（长期有效）或 {@code valid_to > now}；</li>
 *     <li>授权级别 {@code order(permission_level) >= order(requiredLevel)}
 *     （summary &lt; detail &lt; attachment）。</li>
 *     <li>主体按 {@code grantee_type + grantee_id}<b> 成对</b>匹配——不同类型主体的 ID
 *     绝不允许混入同一集合比较（否则「角色ID=5」会错误命中「用户ID=5」的授权）。</li>
 * </ol>
 *
 * <p><b>共享只扩大查看范围</b>（设计文档 §8.19）：本类只回答「该人才对该主体是否可见 / 是否达到
 * 某资料级别」，<b>不</b>代表已获得电话明文、附件下载、背调查看或导出权限；这些动作仍需各自的
 * 按钮权限，并由 {@code TalentScopeDomainService#checkPermissionLevel} 单独判定。</p>
 *
 * <p><b>fail-safe</b>：本方法<b>不抛出任何未捕获异常</b>，任何查询或解析异常都按「无授权」返回
 * {@code false}，避免故障放大为越权放行。</p>
 *
 * <p><b>无缓存</b>：每次判定都实时查询数据库（人才资料按次鉴权，QPS 可控），
 * 因此授权新增 / 撤销 / 过期后立即生效，不存在「缓存未同步失效」的窗口（设计文档 §11.1）。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TalentScopeGrantProviderImpl implements TalentScopeGrantProvider {

    /**
     * 已撤销：否。
     */
    private static final String REVOKE_NO = "0";

    /**
     * 共享授权 Mapper。
     */
    private final TalentScopeGrantMapper talentScopeGrantMapper;

    @Override
    public boolean hasActiveGrant(Long talentId, Collection<GrantSubject> subjects,
                                  TalentPermissionLevelEnum requiredLevel, LocalDateTime now) {
        try {
            if (talentId == null || requiredLevel == null || now == null || subjects == null || subjects.isEmpty()) {
                return false;
            }
            // 只保留「类型 + ID」均有效的成对主体；无效主体直接忽略，不参与条件拼接
            List<GrantSubject> validSubjects = new ArrayList<>(subjects.size());
            for (GrantSubject subject : subjects) {
                if (subject != null && subject.valid()) {
                    validSubjects.add(subject);
                }
            }
            if (validSubjects.isEmpty()) {
                return false;
            }
            // 条件构造顺序：MyBatis-Plus 会把每个新条件「前置」到已有条件之前，
            // 因此这里按最终 SQL 的逆序书写，才能保证主体 OR 分组被完整包在一层括号里
            // （若先写主体 OR、再追加其它条件，MP 会把 OR 分支摊平成相邻同级片段，
            // 退化成「type OR id OR ...」，导致「role#5」错误命中「user#5」的授权）。
            QueryWrapper<TalentScopeGrant> wrapper = new QueryWrapper<>();
            // ① 主体对（最后写入 → 出现在 SQL 最前）：每个主体一个 or(...) 分组，
            //    分组内依次 eq(grantee_type) / eq(grantee_id)，由于 MP 会前置新条件，
            //    最终恰好生成 "(grantee_type = ? AND grantee_id = ?)"（类型与 ID 严格成对绑定）；
            //    多个主体之间即 "(... OR ... OR ...)"。
            //    注意：这里不能再套一层 nested(...)，否则会多出一层不配对的括号。
            //    若先写主体 OR、再追加其它条件，MP 会把 OR 分支摊平成相邻同级片段，
            //    退化成「type OR id OR ...」，导致「role#5」错误命中「user#5」的授权。
            for (GrantSubject subject : validSubjects) {
                wrapper.or(sub -> sub.eq("grantee_type", subject.granteeType())
                    .eq("grantee_id", subject.granteeId()));
            }
            // ② 有效期止：NULL（长期有效）或晚于 now
            wrapper.and(inner -> inner.isNull("valid_to").or().gt("valid_to", now));
            // ③ 有效期起：未填写视为立即生效，故允许为 NULL，或要求「不大于 now」
            wrapper.and(inner -> inner.isNull("valid_from").or().le("valid_from", now));
            // ④ 已撤销授权不参与判定
            wrapper.eq("revoke_flag", REVOKE_NO);
            // ⑤ 逻辑删除由 @TableLogic 自动追加，这里显式再写一次，保证脱离 MP 上下文时语义仍然正确
            wrapper.eq("del_flag", HrTalentConstants.DEL_FLAG_NORMAL);
            // ⑥ 人才维度
            wrapper.eq("talent_id", talentId);
            List<TalentScopeGrant> grants = talentScopeGrantMapper.selectList(wrapper);
            if (grants == null || grants.isEmpty()) {
                return false;
            }
            return hit(grants, requiredLevel, now);
        } catch (Exception e) {
            // 按拒绝处理：授权查询异常绝不放大为越权放行
            log.error("人才共享授权查询异常，按无授权处理, talentId={}, exception={}",
                talentId, e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 逐条精确判定授权是否命中且级别足够。
     *
     * <p>数据库侧只做粗筛，这里再按 {@link LocalDateTime} 精确复核有效期，
     * 并做权限级别比较，确保「过期立即失效」与「summary 不能当 attachment 用」。</p>
     *
     * @param grants        候选授权行
     * @param requiredLevel 要求的最低授权级别
     * @param now           当前时间
     * @return 是否存在命中行
     */
    private boolean hit(List<TalentScopeGrant> grants, TalentPermissionLevelEnum requiredLevel, LocalDateTime now) {
        for (TalentScopeGrant grant : grants) {
            if (grant == null) {
                continue;
            }
            if (!HrTalentConstants.DEL_FLAG_NORMAL.equals(grant.getDelFlag())) {
                continue;
            }
            if (!REVOKE_NO.equals(grant.getRevokeFlag())) {
                continue;
            }
            if (!inEffectiveRange(grant, now)) {
                continue;
            }
            TalentPermissionLevelEnum granted = TalentPermissionLevelEnum.find(grant.getPermissionLevel());
            if (granted == null) {
                // 未知级别按不满足处理（fail-safe）
                continue;
            }
            if (granted.ordinal() >= requiredLevel.ordinal()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判定授权是否落在有效期内（{@code valid_from <= now} 且 {@code valid_to} 为空或大于 now）。
     *
     * <p>{@code valid_from} 为空视为「立即生效」；{@code valid_to} 为空视为「长期有效」。
     * 注意：{@code valid_to} 与 now 相等即视为<b>已过期</b>（严格大于）。</p>
     *
     * @param grant 授权行
     * @param now   当前时间
     * @return 是否有效
     */
    private boolean inEffectiveRange(TalentScopeGrant grant, LocalDateTime now) {
        LocalDateTime from = grant.getValidFrom();
        if (from != null && from.isAfter(now)) {
            return false;
        }
        LocalDateTime to = grant.getValidTo();
        return to == null || to.isAfter(now);
    }

}
