package org.dromara.creative.service;

import org.dromara.creative.domain.DpDeliveryType;
import org.dromara.creative.domain.DpOutputSpec;
import org.dromara.creative.domain.DpScenarioProfile;
import org.dromara.creative.domain.DpScenarioStep;
import org.dromara.creative.domain.DpWorkspaceSchema;

import java.util.List;

/**
 * 场景配置层·只读查询（V0.2 B1）。
 *
 * <p><b>权威顺序</b>（对照文档 D1）：这里是"场景怎么生产"的权威——交付类型、步骤、输出规格、工作台装配。
 * 但它**不碰**已有的权威：模板可用性在 {@code dp_layout_template}（校验和 + 发布门）、
 * 能力/模型在 {@code aig_*}（治理）、阶段合法性在 {@code DpVisualStageEnum#canMoveTo}（代码，故意不配置化）。</p>
 *
 * <p><b>本轮不改行为</b>：这些接口只读；既有流程（阶段机/闸门/出图/排版）一行判定都没改，
 * 现有页面也不会去调它们。B2 才会让流程按配置驱动。</p>
 *
 * @author creative
 */
public interface ICreativeScenarioConfigService {

    /**
     * 全部启用的交付类型（按 sort_no 升序）。
     *
     * @return 交付类型
     */
    List<DpDeliveryType> listDeliveryTypes();

    /**
     * 按编码查交付类型（支持别名，例如文档里的 ECOM_DETAIL_PAGE → 库里的 ECOM_DETAIL）。
     *
     * @param code 交付类型编码或别名
     * @return 交付类型；查不到返回 null
     */
    DpDeliveryType getDeliveryType(String code);

    /**
     * 取某交付类型的场景档案（已发布优先，其次最新版本）。
     *
     * @param deliveryType 交付类型编码或别名
     * @return 场景档案；没有返回 null
     */
    DpScenarioProfile getScenario(String deliveryType);

    /**
     * 取某交付类型的步骤（按 sort_no 升序）。
     *
     * @param deliveryType 交付类型编码或别名
     * @return 步骤列表；没有返回空列表
     */
    List<DpScenarioStep> listSteps(String deliveryType);

    /**
     * 取某交付类型的工作台装配（已发布优先）。
     *
     * @param deliveryType 交付类型编码或别名
     * @return 工作台装配；没有返回 null
     */
    DpWorkspaceSchema getWorkspace(String deliveryType);

    /**
     * 取某交付类型的输出规格（默认规格排最前，其余按 sort_no）。
     *
     * @param deliveryType 交付类型编码或别名
     * @return 输出规格列表；没有返回空列表
     */
    List<DpOutputSpec> listOutputSpecs(String deliveryType);
}
