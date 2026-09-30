package org.dromara.creative.service;

import org.dromara.creative.domain.DpModuleDefinition;
import org.dromara.creative.domain.DpProjectModule;
import org.dromara.creative.domain.bo.ModuleDefinitionBo;
import org.dromara.creative.domain.bo.ProjectModulePlanBo;
import org.dromara.creative.domain.vo.ProjectModulePlanVo;
import org.dromara.creative.helper.CreativeScreenSkeleton;

import java.util.List;

/**
 * 模块引擎（V0.2 R21，文档 §18/§20/§21）。
 *
 * <p><b>权威顺序</b>：交付类型 → 模块库（{@code dp_module_definition}）→ 项目模块计划
 * （{@code dp_project_module}）→ 屏骨架（{@link CreativeScreenSkeleton}）→ 分镜的屏。
 * 契约文件（{@code creative/screen-skeleton.json}）退居**兜底**：项目没有模块计划时整份生效。</p>
 *
 * <p><b>与"配置只读"的关系</b>：模块库与计划都是配置/数据；读接口只读，
 * 写只有一个入口 {@link #savePlan}（文档 §24 的模块规划页），且写前会检查不可逆状态。</p>
 *
 * @author creative
 */
public interface ICreativeModuleService {

    /**
     * 本次生效的屏：骨架 + **逐屏所属的模块行**（两者一定等长且同序）。
     *
     * <p>为什么要一起返回：分镜生成既要"出哪些屏"（骨架），也要"这一屏来自哪个模块、那个模块配了什么"
     * （人工文案、Workflow、对应卖点）。分两次取会各展开一遍，一旦中间有人改了计划，
     * 就会出现"骨架是 5 屏、归属是 7 屏"这种错位。</p>
     *
     * @param skeleton 屏骨架（没有模块计划时为 null，调用方回落契约文件）
     * @param owners   逐屏所属的模块行（与 {@code skeleton.screens()} 同序同长；没有计划时为空）
     */
    record ActiveScreens(CreativeScreenSkeleton skeleton, List<DpProjectModule> owners) {
    }

    /**
     * 本次生效的屏（项目模块计划优先，没有计划时骨架为 null）。
     *
     * @param taskId       项目ID
     * @param deliveryType 交付类型
     * @return 生效的屏；{@code skeleton()} 为 null 表示"没有模块计划，请回落契约文件"
     */
    ActiveScreens activeScreens(Long taskId, String deliveryType);

    /**
     * 某交付类型的模块库（默认骨架在前、可选模块在后）。
     *
     * @param deliveryType 交付类型编码或别名
     * @return 模块定义；没有返回空列表
     */
    List<DpModuleDefinition> listDefinitions(String deliveryType);

    /**
     * 新建模块定义（V0.2 R27，文档 §24 左栏可编辑）。
     *
     * <p>校验：交付类型必须有已发布场景档案；同交付类型内 {@code moduleCode} 唯一；
     * 屏类型/模块名非空；{@code 1 <= minScreens <= maxScreens}；保真等级只能是 STRICT/LOOSE。</p>
     *
     * @param bo 模块定义
     * @return 保存后的定义（含生成的ID）
     */
    DpModuleDefinition createDefinition(ModuleDefinitionBo bo);

    /**
     * 编辑模块定义（R27）。
     *
     * <p><b>影响面要说清</b>：模块库改了只影响**以后**生成的分镜——已经生成的分镜屏上冻着
     * 当时的屏类型/保真等级/取景，不会回溯改动（这是"历史可追溯"的前提）。</p>
     *
     * @param id 定义ID
     * @param bo 模块定义
     * @return 保存后的定义
     */
    DpModuleDefinition updateDefinition(Long id, ModuleDefinitionBo bo);

    /**
     * 启用/停用模块定义（R27）。停用后：新项目的默认骨架不再包含它，模块规划页也选不到它。
     *
     * @param id      定义ID
     * @param enabled '0' 启用 / '1' 停用
     * @return 保存后的定义
     */
    DpModuleDefinition setDefinitionEnabled(Long id, String enabled);

    /**
     * 删除模块定义（R27，软删）。
     *
     * <p><b>有项目计划在用就拒绝</b>：删掉定义会让那些计划里的模块"查不到定义"，
     * 从而退化成"取景未配置"的兜底文案。要下线应该先**停用**，或者先把项目计划里的它删掉。</p>
     *
     * @param id 定义ID
     * @return 删除结果说明
     */
    String deleteDefinition(Long id);

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

    /**
     * 模块规划视图（V0.2 R22，文档 §24）：左栏模块库 + 中栏当前计划 + 右栏字段 + 屏预览 + 分镜对照。
     *
     * <p><b>只读</b>：不初始化计划、不写库。项目还没有计划时 {@code modules} 为空——
     * 页面上显示的是"按交付类型默认骨架初始化后会长成什么样"，由用户点保存才落库。</p>
     *
     * @param taskId 项目ID
     * @return 模块规划视图
     */
    ProjectModulePlanVo planOf(Long taskId);

    /**
     * 保存模块计划（V0.2 R22，文档 §24）——**模块计划的唯一写入点**。
     *
     * <p>整份覆盖式：软删旧行后按列表顺序重写。写前 fail-closed 检查不可逆状态
     * （分镜已锁定 / 已经出过图 / 已经渲染过详情页版本），命中就拒绝并说明原因，
     * 而不是"先改了再说"——屏集合一变，已有候选图和排版版本就对不上了。</p>
     *
     * @param taskId 项目ID
     * @param bo     模块列表（顺序即出屏顺序）
     * @return 保存后的规划视图（含屏预览与分镜对照，页面保存后直接刷新用）
     */
    ProjectModulePlanVo savePlan(Long taskId, ProjectModulePlanBo bo);
}
