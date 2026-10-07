package org.dromara.aigov.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aigov.domain.AigRouteScenarioBinding;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;

import java.io.Serial;
import java.io.Serializable;

/**
 * 场景强制绑定业务对象 aig_route_scenario_binding
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigRouteScenarioBinding.class, reverseConvertGenerate = false)
public class AigRouteScenarioBindingBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定ID（编辑时必填）
     */
    @NotNull(message = "绑定ID不能为空", groups = {EditGroup.class})
    private Long bindId;

    /**
     * 场景编码
     * <p>刻意限制字符集：场景编码会出现在路由说明、审计与排障输出里，
     * 允许任意字符会让「差不多但不一样」的两个编码（空格/全角）都配得上，
     * 事后从日志上分辨不出为什么某个场景没被钉住。</p>
     */
    @NotBlank(message = "场景编码不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 64, message = "场景编码长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "场景编码只能包含字母、数字、下划线、中划线",
        groups = {AddGroup.class, EditGroup.class})
    private String scenarioCode;

    /**
     * 能力编码
     */
    @NotBlank(message = "能力编码不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 64, message = "能力编码长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String capabilityCode;

    /**
     * 强制使用的供应商ID
     */
    @NotNull(message = "供应商不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long providerId;

    /**
     * 优先序（升序；仅用于多个被允许供应商之间的稳定排序）
     */
    private Integer priority;

    /**
     * 状态（0正常 1停用）
     */
    @Pattern(regexp = "^(0|1)?$", message = "状态只能为 0/1", groups = {AddGroup.class, EditGroup.class})
    private String status;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

    /**
     * 场景编码（查询条件，模糊匹配）
     */
    @Size(max = 64, message = "场景编码长度不能超过 64", groups = {QueryGroup.class})
    private String scenarioCodeLike;

}
