package org.dromara.content.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.bo.ContentGateRuleBo;
import org.dromara.content.domain.vo.CpGateRuleVo;

import java.util.List;

/**
 * 闸门规则服务（表驱动，运营可改）。
 *
 * @author content
 */
public interface IContentGateRuleService {

    /**
     * 分页查询。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<CpGateRuleVo> queryPage(ContentGateRuleBo bo, PageQuery pageQuery);

    /**
     * 详情。
     *
     * @param ruleId 规则ID
     * @return 规则
     */
    CpGateRuleVo getDetail(Long ruleId);

    /**
     * 新增。
     *
     * @param bo 规则参数
     * @return 规则ID
     */
    Long create(ContentGateRuleBo bo);

    /**
     * 修改。
     *
     * @param bo 规则参数
     */
    void update(ContentGateRuleBo bo);

    /**
     * 删除。
     *
     * @param ruleId 规则ID
     */
    void remove(Long ruleId);

    /**
     * 取某交付类型下启用的规则（供闸门判定与预检使用）。
     *
     * @param deliverableType 交付类型
     * @return 规则列表（按 sort_no 升序）
     */
    List<org.dromara.content.domain.CpGateRule> listEnabledRules(String deliverableType);

    /**
     * 取所有启用规则涉及的事实字段编码（不限交付类型）。
     *
     * <p>用于校验「手工录入的字段编码是不是治理侧认得出来的编码」。闸门规则是表驱动、
     * 运营可改的，因此不能只拿别名表当白名单——否则运营新加一条规则，手工录入反而被拒。</p>
     *
     * @return 字段编码集合
     */
    java.util.Set<String> allEnabledFieldCodes();

}
