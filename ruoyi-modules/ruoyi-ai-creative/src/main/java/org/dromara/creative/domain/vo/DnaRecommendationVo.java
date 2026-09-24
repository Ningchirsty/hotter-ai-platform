package org.dromara.creative.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 视觉基因「按参考图推荐」结果。
 *
 * <p>字段形状与编辑表单一致，页面可直接灌进表单让用户过一眼再保存；同时带回**逐字段依据**、
 * 可信度、测不出来的字段说明，以及与已确认事实的冲突提示——推荐值不能用「看起来很像」的方式塞给用户。</p>
 *
 * @author creative
 */
@Data
public class DnaRecommendationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否真的分析成功（没有参考图/解不开时为 false，其余字段为空）
     */
    private boolean analyzed;

    /**
     * 参考图信息
     */
    private String imageName;

    private Integer imageWidth;

    private Integer imageHeight;

    // ---------------- 推荐值（与编辑表单同形） ----------------

    private String colorPrimary;

    private String colorSecondary;

    private String colorAccent;

    private String colorBg;

    private String saturation;

    private String contrastLevel;

    private String whitespaceLevel;

    private String sceneType;

    private String lightingType;

    private String lightingDir;

    private Integer productRatioMin;

    private Integer productRatioMax;

    /**
     * 实测产品占画面比例（%），用于解释占比区间是怎么来的
     */
    private Double observedProductRatio;

    // ---------------- 依据与说明 ----------------

    /**
     * 逐字段依据：field / value / basis / reliability
     */
    private List<Map<String, Object>> evidence = new ArrayList<>();

    /**
     * 与已确认事实的冲突（例如事实颜色与实测主色不一致）
     */
    private List<String> conflicts = new ArrayList<>();

    /**
     * 测不出来的字段与原因（不猜）
     */
    private List<String> skipped = new ArrayList<>();

    /**
     * 整体说明
     */
    private List<String> notes = new ArrayList<>();

}
