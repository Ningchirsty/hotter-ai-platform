package org.dromara.hrtalent.service.talent;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.talent.TalentScopeGrantBo;
import org.dromara.hrtalent.domain.bo.talent.TalentScopeGrantQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentScopeGrantVo;

/**
 * 人才共享授权 服务接口（SPEC-P4 §2.4、设计文档 §8.19 / §21.14）。
 *
 * <p><b>共享只扩大查看范围</b>（设计文档 §8.19）：本服务的授权<b>不</b>自动授予电话明文、
 * 附件下载、背调查看与导出权限；授权级别的校验一律委托
 * {@code TalentScopeDomainService#checkPermissionLevel}，禁止自写规则。</p>
 *
 * <p><b>过期立即失效</b>（设计文档 §11.1）：本服务不引入任何授权缓存，
 * 新增 / 撤销后无需缓存失效动作即即时生效。</p>
 *
 * @author hr-talent
 */
public interface ITalentScopeGrantService {

    /**
     * 分页查询某位人才的共享授权列表（默认只返回当前有效授权）。
     *
     * @param talentId  人才主档ID
     * @param bo        检索条件，可为 null
     * @param pageQuery 分页参数
     * @return 授权分页结果
     */
    PageResult<TalentScopeGrantVo> queryPage(Long talentId, TalentScopeGrantQueryBo bo, PageQuery pageQuery);

    /**
     * 分页查询当前用户可见范围内的共享授权列表（跨人才）。
     *
     * @param bo        检索条件，可为 null（{@code talentId} 为空时按可见范围过滤）
     * @param pageQuery 分页参数
     * @return 授权分页结果
     */
    PageResult<TalentScopeGrantVo> queryVisiblePage(TalentScopeGrantQueryBo bo, PageQuery pageQuery);

    /**
     * 查询单条授权详情（不返回授权级别不足的人才授权）。
     *
     * @param grantId 授权ID
     * @return 授权详情
     */
    TalentScopeGrantVo getDetail(Long grantId);

    /**
     * 新增共享授权。
     *
     * <p>调用方必须先对目标人才具备不低于所授级别的授权（或为超管 / 集团级管理员），
     * 否则拒绝——不允许通过授权把看不到的资料转授给他人。</p>
     *
     * @param talentId 人才主档ID（路径参数，权威值）
     * @param bo       授权入参
     * @return 新增的授权ID
     */
    Long create(Long talentId, TalentScopeGrantBo bo);

    /**
     * 撤销共享授权（逻辑撤销：置 {@code revoke_flag='1'} 并记录撤销人与撤销时间）。
     *
     * <p>撤销后该授权立即不再参与可见性判定（设计文档 §11.1），且不物理删除以保留审计轨迹。</p>
     *
     * @param grantId  授权ID
     * @param reason   撤销原因，可为空（会追加到备注）
     */
    void revoke(Long grantId, String reason);

    /**
     * 逻辑删除共享授权（数据清理用；日常业务请使用 {@link #revoke}）。
     *
     * @param grantIds 授权ID集合
     */
    void remove(Long[] grantIds);

}
