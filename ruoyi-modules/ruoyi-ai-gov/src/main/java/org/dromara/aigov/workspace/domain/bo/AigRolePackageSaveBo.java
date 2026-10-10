package org.dromara.aigov.workspace.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 岗位包草稿保存入参（主文档线增量 1b）。
 *
 * <h3>"新建岗位"与"覆盖草稿"共用一个入参</h3>
 * <p>{@code roleId} 为空即新建岗位（同时建 DRAFT 版本）；{@code roleVersionId} 非空即覆盖
 * 某个已存在的 <b>DRAFT</b> 版本。已发布版本不可覆盖——服务层会拒绝，
 * 前端藏按钮不算约束。</p>
 *
 * <h3>为什么覆盖要带 {@code expectedManifestSha256}</h3>
 * <p>两个人同时编辑同一份草稿时，后保存的会把先保存的一字不响地盖掉。带上"我读到的是哪一版"，
 * 服务层就能发现这件事并拒绝（CAS），而不是让其中一个人的工作消失。</p>
 *
 * @author ai-gov
 */
@Data
public class AigRolePackageSaveBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位定义ID（空=新建岗位）
     */
    private Long roleId;

    /**
     * 岗位版本ID（空=新建 DRAFT 版本；非空=覆盖该草稿版本）
     */
    private Long roleVersionId;

    /**
     * 覆盖草稿时必填：保存前读到的清单哈希（CAS）
     */
    @Size(max = 64, message = "清单哈希长度不合法")
    private String expectedManifestSha256;

    /**
     * 岗位编码（跨版本稳定）
     */
    @NotBlank(message = "岗位编码不能为空")
    @Size(max = 80, message = "岗位编码长度不能超过 80")
    private String roleCode;

    /**
     * 岗位名称
     */
    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 160, message = "岗位名称长度不能超过 160")
    private String roleName;

    /**
     * 版本号（SemVer 风格，如 1.0.0）
     */
    @NotBlank(message = "版本号不能为空")
    @Size(max = 32, message = "版本号长度不能超过 32")
    private String version;

    /**
     * 岗位简介（员工看到的说明）
     */
    @Size(max = 500, message = "岗位简介长度不能超过 500")
    private String description;

    /**
     * 分类（至少一个）
     */
    @Valid
    private List<Category> categories;

    /**
     * 默认分类（必须在 categories 里）
     */
    @Size(max = 80, message = "默认分类长度不能超过 80")
    private String defaultCategory;

    /**
     * 能力卡片
     */
    @Valid
    private List<Action> actions;

    /**
     * 可见范围（如 ASSIGNED_ORG）
     */
    @NotBlank(message = "可见范围不能为空")
    @Size(max = 64, message = "可见范围长度不能超过 64")
    private String audienceScope;

    /**
     * 默认数据等级
     */
    @NotBlank(message = "默认数据等级不能为空")
    @Size(max = 32, message = "默认数据等级长度不能超过 32")
    private String defaultDataLevel;

    /**
     * 最高数据等级（只能比默认等级更严或相等）
     */
    @NotBlank(message = "最高数据等级不能为空")
    @Size(max = 32, message = "最高数据等级长度不能超过 32")
    private String maxDataLevel;

    /**
     * 版本说明
     */
    @Size(max = 500, message = "版本说明长度不能超过 500")
    private String remark;

    /**
     * 分类入参。
     *
     * @author ai-gov
     */
    @Data
    public static class Category implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 分类编码
         */
        @NotBlank(message = "分类编码不能为空")
        @Size(max = 80, message = "分类编码长度不能超过 80")
        private String code;

        /**
         * 分类名称
         */
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 160, message = "分类名称长度不能超过 160")
        private String name;
    }

    /**
     * 能力卡片入参。
     *
     * @author ai-gov
     */
    @Data
    public static class Action implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 卡片编码
         */
        @NotBlank(message = "卡片编码不能为空")
        @Size(max = 64, message = "卡片编码长度不能超过 64")
        private String code;

        /**
         * 所属分类
         */
        @NotBlank(message = "卡片分类不能为空")
        @Size(max = 64, message = "卡片分类长度不能超过 64")
        private String categoryCode;

        /**
         * 卡片标题
         */
        @NotBlank(message = "卡片标题不能为空")
        @Size(max = 160, message = "卡片标题长度不能超过 160")
        private String title;

        /**
         * 卡片说明
         */
        @Size(max = 500, message = "卡片说明长度不能超过 500")
        private String description;

        /**
         * 启动方式
         */
        @NotBlank(message = "启动方式不能为空")
        @Size(max = 16, message = "启动方式长度不能超过 16")
        private String launchMode;

        /**
         * 目标类型
         */
        @NotBlank(message = "目标类型不能为空")
        @Size(max = 24, message = "目标类型长度不能超过 24")
        private String targetType;

        /**
         * 目标引用
         */
        @Size(max = 255, message = "目标引用长度不能超过 255")
        private String targetRef;

        /**
         * STUDIO 模式的专业页跳转键
         */
        @Size(max = 64, message = "跳转键长度不能超过 64")
        private String studioRouteKey;

        /**
         * 所需上下文键（逗号分隔）
         */
        @Size(max = 255, message = "上下文键长度不能超过 255")
        private String requiredContext;

        /**
         * 排序（同分类内）
         */
        private Integer sortOrder;

        /**
         * 是否启用
         */
        private Boolean enabled;
    }

}
