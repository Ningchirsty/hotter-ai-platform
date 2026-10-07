package org.dromara.aigov.agent.service;

import org.dromara.aigov.agent.domain.bo.AigAgentBindingQueryBo;
import org.dromara.aigov.agent.domain.bo.AigAgentQueryBo;
import org.dromara.aigov.agent.domain.bo.AigAgentVersionQueryBo;
import org.dromara.aigov.agent.domain.bo.AigPackageQueryBo;
import org.dromara.aigov.agent.domain.bo.AigPackageVersionQueryBo;
import org.dromara.aigov.agent.domain.bo.AigSkillQueryBo;
import org.dromara.aigov.agent.domain.bo.AigSkillVersionQueryBo;
import org.dromara.aigov.agent.domain.vo.AigAgentBindingVo;
import org.dromara.aigov.agent.domain.vo.AigAgentVersionVo;
import org.dromara.aigov.agent.domain.vo.AigAgentVo;
import org.dromara.aigov.agent.domain.vo.AigPackageVersionVo;
import org.dromara.aigov.agent.domain.vo.AigPackageVo;
import org.dromara.aigov.agent.domain.vo.AigSkillVersionVo;
import org.dromara.aigov.agent.domain.vo.AigSkillVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * 注册中心的只读查询（设计 §5、§6、§10.2）。
 *
 * <p><b>为什么把「查询」与「发布推进」分成两个服务</b>：查询只有读语义，而
 * {@link IAigAgentRegistryService} 里每一个方法都可能改变发布状态。分开之后，
 * 页面/接口的读路径不需要（也不应该）拿到发布推进的能力，评审时看接口名就知道
 * 「这东西会不会改状态」。两者共用同一批 Mapper，仍然只有一个写入口。</p>
 *
 * <p>列表一律返回裁剪过的 VO（与 {@code AigTaskVo} 同口径：不带 longtext 与结构化配置）——
 * 清单页不需要 Manifest 原文，把长文本塞进列表响应只会让页面变慢。</p>
 *
 * @author ai-gov
 */
public interface IAigAgentRegistryQueryService {

    /**
     * 分页查询 Agent 清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页
     * @return 分页结果
     */
    PageResult<AigAgentVo> queryAgentPage(AigAgentQueryBo bo, PageQuery pageQuery);

    /**
     * 取 Agent 详情。
     *
     * @param agentId Agent ID
     * @return 详情
     */
    AigAgentVo getAgent(Long agentId);

    /**
     * 分页查询 Agent 版本清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页
     * @return 分页结果
     */
    PageResult<AigAgentVersionVo> queryAgentVersionPage(AigAgentVersionQueryBo bo, PageQuery pageQuery);

    /**
     * 取 Agent 版本详情。
     *
     * @param agentVersionId 版本ID
     * @return 详情
     */
    AigAgentVersionVo getAgentVersion(Long agentVersionId);

    /**
     * 分页查询 Skill 清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页
     * @return 分页结果
     */
    PageResult<AigSkillVo> querySkillPage(AigSkillQueryBo bo, PageQuery pageQuery);

    /**
     * 分页查询 Skill 版本清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页
     * @return 分页结果
     */
    PageResult<AigSkillVersionVo> querySkillVersionPage(AigSkillVersionQueryBo bo, PageQuery pageQuery);

    /**
     * 分页查询 Package 清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页
     * @return 分页结果
     */
    PageResult<AigPackageVo> queryPackagePage(AigPackageQueryBo bo, PageQuery pageQuery);

    /**
     * 取 Package 详情。
     *
     * @param packageId Package ID
     * @return 详情
     */
    AigPackageVo getPackage(Long packageId);

    /**
     * 分页查询 Package 版本清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页
     * @return 分页结果
     */
    PageResult<AigPackageVersionVo> queryPackageVersionPage(AigPackageVersionQueryBo bo,
                                                            PageQuery pageQuery);

    /**
     * 取 Package 版本详情（含 scan_result/scan_detail；Manifest 原文不在 VO 里）。
     *
     * @param packageVersionId 版本ID
     * @return 详情
     */
    AigPackageVersionVo getPackageVersion(Long packageVersionId);

    /**
     * 分页查询 Agent 版本绑定清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页
     * @return 分页结果
     */
    PageResult<AigAgentBindingVo> queryBindingPage(AigAgentBindingQueryBo bo, PageQuery pageQuery);

}
