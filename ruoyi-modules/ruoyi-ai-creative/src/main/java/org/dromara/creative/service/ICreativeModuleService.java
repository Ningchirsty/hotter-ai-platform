package org.dromara.creative.service;

import org.dromara.creative.domain.DpModuleDefinition;
import org.dromara.creative.domain.DpProjectModule;
import org.dromara.creative.helper.CreativeScreenSkeleton;

import java.util.List;

/**
 * 模块引擎（V0.2 R21，文档 §18/§20/§21）。
 *
 * <p><b>权威顺序</b>：交付类型 → 模块库（{@code dp_module_definition}）→ 项目模块计划
 * （{@code dp_project_module}）→ 屏骨架（{@link CreativeScreenSkeleton}）→ 分镜的屏。
 * 契约文件（{@code creative/screen-skeleton.json}）退居**兜底**：项目没有模块计划时整份生效。</p>
 *
 * <p><b>与"配置只读"的关系</b>：模块库与计划都是配置/数据；本轮只提供<b>读</b>与
 * "按默认骨架初始化项目计划"（幂等，且只在分镜生成这类写动作里触发）。
 * 文档 §24 的模块规划页面（拖拽/增删）是后续工作。</p>
 *
 * @author creative
 */
public interface ICreativeModuleService {

    /**
     * 某交付类型的模块库（默认骨架在前、可选模块在后）。
     *
     * @param deliveryType 交付类型编码或别名
     * @return 模块定义；没有返回空列表
     */
    List<DpModuleDefinition> listDefinitions(String deliveryType);

    /**
     * 某项目的模块计划（按 sortNo）。
     *
     * <p><b>只读</b>：不初始化、不写库（读接口不该有副作用）。</p>
     *
     * @param taskId 项目ID
     * @return 模块计划；没有返回空列表
     */
    List<DpProjectModule> listProjectModules(Long taskId);

    /**
     * 确保项目有模块计划：没有就按交付类型的**默认骨架**（{@code default_selected=1}）初始化。
     *
     * <p>幂等：已有计划直接返回（不覆盖人工调整）。初始化发生在"分镜生成"这类写动作里，
     * 因此这里写库是合规的。</p>
     *
     * @param taskId       项目ID
     * @param deliveryType 交付类型（可空：空则无法初始化，返回现有计划）
     * @return 项目模块计划（按 sortNo）
     */
    List<DpProjectModule> ensureProjectModules(Long taskId, String deliveryType);

    /**
     * 把项目模块计划翻成屏骨架（文档 §21 的 Module Plan → Screen Plan）。
     *
     * <p>一个模块占 {@code screenCount} 屏；单屏用模块名做展示名，多屏用「模块名 + 一二三…」
     * （契约校验要求展示名唯一，这也是历史展示名"卖点一/卖点二"的来历）。</p>
     *
     * @param taskId       项目ID
     * @param deliveryType 交付类型
     * @return 屏骨架；项目没有模块计划且无法初始化时返回 null（调用方回落契约文件）
     */
    CreativeScreenSkeleton skeletonOf(Long taskId, String deliveryType);
}
