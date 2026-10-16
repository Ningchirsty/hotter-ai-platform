package org.dromara.aigov.service;

import org.dromara.aigov.domain.bo.AigCapabilityModelBo;
import org.dromara.aigov.domain.vo.AigCapabilityModelVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * AI 能力-模型绑定服务。
 *
 * @author ai-gov
 */
public interface IAigModelBindingService {

    /**
     * 分页查询绑定关系。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<AigCapabilityModelVo> queryPage(AigCapabilityModelBo bo, PageQuery pageQuery);

    /**
     * 查询某能力的启用绑定列表（按用途与优先级排序）。
     *
     * @param capabilityCode 能力编码
     * @return 绑定列表
     */
    List<AigCapabilityModelVo> listByCapability(String capabilityCode);

    /**
     * 新增绑定（能力 × 模型唯一）。
     *
     * @param bo 绑定参数
     * @return 新绑定ID
     */
    Long create(AigCapabilityModelBo bo);

    /**
     * 编辑候选的用途与优先级（<b>不能换模型</b>）。
     *
     * <p><b>为什么必须有这个方法</b>：在此之前，"候选"只有新增与删除两个动作
     * （{@code POST} / {@code DELETE}），而"用途（PRIMARY/FALLBACK/GRAY）"与"优先级"是路由排序的
     * 直接输入 —— 配错一次只能删了重建，删的瞬间这条候选在路由里消失（生产上就是这段时间没有候选）。</p>
     *
     * <p><b>为什么不支持换模型</b>：换模型等于换了一条候选的身份（模型主数据、部署类型、健康、
     * 成本上限全都变），"改"会让变更记录与审计看起来像"同一个候选改了属性"，
     * 而真实发生的是"另一个模型接管了"。因此换模型请新增候选 + 删除旧候选（接口层面不做）。</p>
     *
     * @param bindId 绑定ID
     * @param bo     绑定参数（只取 usageType / priority / status / remark）
     */
    void update(Long bindId, AigCapabilityModelBo bo);

    /**
     * 删除绑定（逻辑删除）。
     *
     * @param bindId 绑定ID
     */
    void remove(Long bindId);

}
