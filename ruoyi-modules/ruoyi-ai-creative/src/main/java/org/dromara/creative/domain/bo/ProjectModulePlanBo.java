package org.dromara.creative.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 保存模块计划请求（V0.2 R22，文档 §24）。
 *
 * <p><b>整份覆盖式保存</b>：页面把左中右三栏的编辑结果一次性提交，服务端软删旧行、按列表顺序重写。
 * 之所以不做"逐行增删改"的细粒度接口：§24 的拖拽排序、复制、启停都要求"顺序"是一次事务里的整体事实，
 * 细粒度接口会把顺序写成 N 次局部更新，中途失败就留下一个半截顺序——而分镜就是按这个顺序出屏的。</p>
 *
 * <p>字段校验放在服务里（要给出中文可读原因、还要对着模块库的 min/maxScreens 判），
 * 因此这里不加注解，避免"注解报错"和"业务报错"两套口径。</p>
 *
 * @author creative
 */
@Data
public class ProjectModulePlanBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模块列表（顺序即出屏顺序）
     */
    private List<Item> modules = new ArrayList<>();

    /**
     * 一个模块（对应 dp_project_module 一行）。
     */
    @Data
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 模块编码（必须能在模块库里找到，或同时给出 moduleName + screenType） */
        private String moduleCode;

        /** 模块名（可覆盖库里的名字；多屏时作为"卖点一/卖点二"的前缀） */
        private String moduleName;

        /** 屏类型（可覆盖；不覆盖时取库里的） */
        private String screenType;

        /** 本模块占几屏（1..maxScreens） */
        private Integer screenCount;

        /** 是否启用（'0'启用 '1'停用；不传按启用处理） */
        private String enabled;

        /** 模块目标（右栏，可空） */
        private String objective;

        /** 对应卖点（文案块编码，逗号分隔，可空） */
        private String sellingPointCodes;

        /** 人工文案（写了就用它，可空） */
        private String copyText;

        /** 所需事实（事实字段码，逗号分隔，可空） */
        private String requiredFactCodes;

        /** 视觉表达（JSON 文本，可空） */
        private String visualRulesJson;

        /** 参考图（文件ID/编码，逗号分隔，可空） */
        private String referenceCodes;

        /** Workflow/能力编码（逗号分隔，取第一个用于出图，可空） */
        private String workflowCodes;

        /** 模板码（逗号分隔，可空） */
        private String templateCodes;

        /** 备注（可空） */
        private String remark;
    }
}
