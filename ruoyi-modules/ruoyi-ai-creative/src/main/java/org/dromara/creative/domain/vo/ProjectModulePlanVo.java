package org.dromara.creative.domain.vo;

import lombok.Data;
import org.dromara.creative.domain.DpModuleDefinition;
import org.dromara.creative.domain.DpProjectModule;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 模块规划视图（V0.2 R22，文档 §24）。
 *
 * <p>它是"模块规划页面"一次读取所需的全部东西：**左栏**模块库（{@link #library}）、
 * **中栏**当前顺序（{@link #modules}）、**右栏**逐模块字段（就在 {@code modules} 的行里）、
 * 以及两块"别让用户猜"的东西：{@link #screens}（这份计划会长成哪些屏的**预览**）与
 * {@link #storyboard}（最近一次分镜与当前计划的**差异**）。</p>
 *
 * <p>为什么不把"能不能改"放在前端算：判定要看分镜是否锁定、是否已出图、是否已渲染，
 * 那是后端的权威状态。前端拿 {@link #editable} / {@link #editBlockReason} 直接展示，
 * 自己不去猜（猜出来的禁用态一旦与后端不一致，用户就会遇到"按钮能点但保存被拒"）。</p>
 *
 * @author creative
 */
@Data
public class ProjectModulePlanVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目ID
     */
    private Long taskId;

    /**
     * 交付类型编码
     */
    private String deliveryType;

    /**
     * 交付类型名称（展示用；查不到时等于编码）
     */
    private String deliveryName;

    /**
     * 现在能不能改计划（false 时看 editBlockReason）
     */
    private Boolean editable;

    /**
     * 不能改的原因（可读的一句话，直接展示给用户）
     */
    private String editBlockReason;

    /**
     * 当前计划（按 sortNo，**含停用行**——停用不是删除）
     */
    private List<DpProjectModule> modules = new ArrayList<>();

    /**
     * 该交付类型的模块库（默认骨架在前，可选模块在后）
     */
    private List<DpModuleDefinition> library = new ArrayList<>();

    /**
     * 这份计划会出哪些屏（Module Plan → Screen Plan 的预览，与分镜生成用的是同一段展开逻辑）
     */
    private List<ScreenPreview> screens = new ArrayList<>();

    /**
     * 预览屏数（= screens.size()，单独给一个字段便于页面直接显示）
     */
    private Integer screenCount = 0;

    /**
     * 屏预览的来源：{@code PLAN}=本项目已保存的模块计划；{@code DEFAULT_SKELETON}=项目还没有计划，
     * 给的是"按交付类型默认骨架初始化后会长成什么样"（**尚未落库**）。
     *
     * <p>为什么必须标出来：两种情况在页面上长得一模一样，但一个是"这个项目的事实"，
     * 另一个是"你还没保存的默认值"。不标的话用户会以为项目已经有计划了。</p>
     */
    private String previewSource;

    /**
     * 屏预览的一句人话说明（页面直接显示）
     */
    private String previewNote;

    /**
     * 最近一次分镜与当前计划的对照（没有分镜时为 null）
     */
    private StoryboardRef storyboard;

    /**
     * 一屏的预览。
     *
     * @param screenNo         屏号（S01…，与分镜生成时的编号规则一致）
     * @param moduleCode       来自哪个模块
     * @param moduleName       模块名
     * @param screenType       屏类型
     * @param label            展示名（多屏会带中文序号）
     * @param productLockLevel 产品保真等级
     * @param shot             取景（{ratio} 未代入——预览阶段还不知道基因）
     * @param enabled          这个模块是否启用（停用的模块不出现在预览里，这里恒为 true；
     *                         保留字段是为了以后要显示"停用的屏"时不用改协议）
     * @param missingFacts     该模块"所需事实"里**还没有确认值**的那些码（空表示都齐了）
     */
    public record ScreenPreview(String screenNo, String moduleCode, String moduleName, String screenType,
                                String label, String productLockLevel, String shot, boolean enabled,
                                List<String> missingFacts) implements Serializable {
    }

    /**
     * 最近一次分镜与当前计划的对照。
     *
     * @param storyboardId 分镜ID
     * @param version      版本号
     * @param status       状态（DRAFT/LOCKED）
     * @param screenCount  该分镜的屏数
     * @param screenTypes  该分镜的屏类型（按顺序）
     * @param stale        是否与当前计划**已经不一致**（屏数或屏类型顺序不同）——
     *                     不一致时页面上要明说"要重新拆分镜才是新屏集合"，不能让用户以为改完就生效了
     * @param note         可读说明
     */
    public record StoryboardRef(Long storyboardId, Integer version, String status, Integer screenCount,
                               List<String> screenTypes, boolean stale, String note) implements Serializable {
    }
}
