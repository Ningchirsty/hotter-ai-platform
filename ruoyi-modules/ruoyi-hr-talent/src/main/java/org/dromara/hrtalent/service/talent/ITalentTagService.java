package org.dromara.hrtalent.service.talent;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileTagBo;
import org.dromara.hrtalent.domain.bo.talent.TalentTagBo;
import org.dromara.hrtalent.domain.bo.talent.TalentTagQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentTagVo;

import java.util.List;

/**
 * 人才标签服务接口（SPEC-P4 §2.3 C 线，设计文档 §8.15、§7.6.4）。
 *
 * <p><b>敏感与歧视性标签硬约束</b>（实现方必须落实并在服务层给出中文提示）：</p>
 * <ul>
 *     <li>禁止随意创建敏感或歧视性标签：标签名称命中敏感/歧视词库时一律拒绝（设计文档 §8.15）；</li>
 *     <li>{@code sensitive_flag = '1'} 的敏感标签只能由集团级人才管理员维护，
 *     且对普通用户不出现在标签字典查询结果中；</li>
 *     <li><b>背调失败、健康、家庭、年龄等敏感内容不得自动生成可被普通用户检索的标签</b>：
 *     自动来源（解析/系统）的标签关系不得挂敏感标签（设计文档 §7.6.4）。</li>
 * </ul>
 *
 * <p><b>授权约束</b>：人才侧的读写都必须先经
 * {@code org.dromara.hrtalent.domainservice.TalentScopeDomainService} 的资源级鉴权
 * （实现方复用 {@link ITalentProfileService#requireVisible(Long)}），
 * 不得自行拼装授权规则（设计文档 §11.1）。</p>
 *
 * @author hr-talent
 */
public interface ITalentTagService {

    /**
     * 分页查询标签字典（敏感标签按当前用户身份过滤）。
     *
     * @param bo        检索条件，可为空
     * @param pageQuery 分页参数
     * @return 标签分页结果
     */
    PageResult<TalentTagVo> queryPage(TalentTagQueryBo bo, PageQuery pageQuery);

    /**
     * 标签详情。
     *
     * @param tagId 标签ID
     * @return 标签详情
     */
    TalentTagVo getDetail(Long tagId);

    /**
     * 新增标签（名称与敏感标记经服务层校验，编码由服务端生成）。
     *
     * @param bo 标签入参
     * @return 新增的标签ID
     */
    Long create(TalentTagBo bo);

    /**
     * 更新标签（名称与敏感标记经服务层校验）。
     *
     * @param bo 标签入参
     */
    void update(TalentTagBo bo);

    /**
     * 逻辑删除标签（由人才池管理员维护）。
     *
     * @param tagIds 标签ID数组
     */
    void remove(Long[] tagIds);

    /**
     * 查询某人才当前有效的标签（只返回对该人才可见的调用方可见的标签）。
     *
     * @param talentId 人才主档ID
     * @return 标签列表（不为 null）
     */
    List<TalentTagVo> listProfileTags(Long talentId);

    /**
     * 更新人才的标签关系（全量覆盖语义，差集增删，重复挂载按幂等处理）。
     *
     * @param talentId 人才主档ID
     * @param bo       标签关系入参
     */
    void updateProfileTags(Long talentId, TalentProfileTagBo bo);

}
