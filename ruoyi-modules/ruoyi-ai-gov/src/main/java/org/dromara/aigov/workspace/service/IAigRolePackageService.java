package org.dromara.aigov.workspace.service;

import org.dromara.aigov.workspace.domain.bo.AigRolePackageQueryBo;
import org.dromara.aigov.workspace.domain.bo.AigRolePackageSaveBo;
import org.dromara.aigov.workspace.domain.vo.AigRolePackageValidateVo;
import org.dromara.aigov.workspace.domain.vo.AigRoleVersionDetailVo;
import org.dromara.aigov.workspace.domain.vo.AigRoleVersionVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * 岗位包（Role Package）服务（主文档线增量 1b，附件 §5）。
 *
 * <h3>一条贯穿全链路的规则：<b>发布后不可变</b></h3>
 * <p>岗位版本一旦离开 DRAFT 就不能再改清单/卡片，要改就出新版本。
 * 理由与 {@code aig_agent_version} 同口径：员工曾经看到的是哪一份配置，必须事后能回答。
 * 因此除了 {@link #saveDraft} 之外，没有任何方法会改一份已发布版本的清单。</p>
 *
 * <h3>发布不是"改个字段"</h3>
 * <p>{@link #advance} 只按 {@code AigRoleReleaseTransition} 写死的边表走，且
 * <b>要离开 DRAFT 必须先校验通过</b>（发布一份自己都配错的岗位包 =
 * 让员工看到点不动的卡片）。停用则是可撤销的。</p>
 *
 * @author ai-gov
 */
public interface IAigRolePackageService {

    /**
     * 分页查询岗位包版本（跨岗位）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<AigRoleVersionVo> queryPage(AigRolePackageQueryBo bo, PageQuery pageQuery);

    /**
     * 某个岗位的全部版本（最新在前）。
     *
     * @param roleId 岗位定义ID
     * @return 版本清单
     */
    List<AigRoleVersionVo> listVersions(Long roleId);

    /**
     * 版本详情（含卡片、清单原文与当前校验结论）。
     *
     * @param roleVersionId 岗位版本ID
     * @return 详情
     */
    AigRoleVersionDetailVo getVersionDetail(Long roleVersionId);

    /**
     * 预检一份"还没入库"的岗位包（只读，不落库）。
     *
     * @param bo 草稿入参
     * @return 预检结论（含清单哈希，便于校验后立刻保存）
     */
    AigRolePackageValidateVo validate(AigRolePackageSaveBo bo);

    /**
     * 校验库里已存在的那一份版本（发布前必做）。
     *
     * <p>与 {@link #validate} 的区别：它读的是<b>库里的清单与卡片行</b>，
     * 因此能发现"清单与卡片表漂移"这类入库后才出现的问题。</p>
     *
     * @param roleVersionId 岗位版本ID
     * @return 预检结论
     */
    AigRolePackageValidateVo validateStored(Long roleVersionId);

    /**
     * 保存草稿（新建岗位/新版本，或覆盖一个 DRAFT 版本）。
     *
     * <p><b>刻意不要求校验通过</b>：草稿就是"还没配好"的东西，能存下来才能继续配。
     * 门槛放在发布那一步——那里必须通过。</p>
     *
     * @param bo      草稿入参
     * @param actorId 操作者（从登录态取）
     * @return 保存后的详情（含校验结论）
     */
    AigRoleVersionDetailVo saveDraft(AigRolePackageSaveBo bo, Long actorId);

    /**
     * 发布流转（DRAFT→TESTING→PUBLISHED，以及 →DISABLED 与 DISABLED 的重新启用）。
     *
     * @param roleVersionId 岗位版本ID
     * @param targetStatus  目标状态
     * @param remark        流转说明
     * @param actorId       操作者
     * @return 流转后的详情
     */
    AigRoleVersionDetailVo advance(Long roleVersionId, String targetStatus, String remark, Long actorId);

}
