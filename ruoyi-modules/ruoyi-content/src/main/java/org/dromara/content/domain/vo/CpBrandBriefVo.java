package org.dromara.content.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 品牌 Brief 展示对象。
 *
 * <p><b>字段名即契约</b>：前端（内容任务页与视觉项目页）都按这些名字取数，
 * 因此不做改名，也不把行文本拆成数组——前端就是多行文本框，
 * 拆成数组会让「一行一条」这个约定在两端各存一份（迟早不一致）。</p>
 *
 * <p>{@code configured=false} 时其余业务字段全为 null，页面据此显示「还没填」并给出录入入口；
 * 记录不存在时也不返回 null 的 data，避免前端把「没填」与「接口坏了」当成同一件事。</p>
 *
 * @author content
 */
@Data
public class CpBrandBriefVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 内容任务ID（= 视觉项目 taskId，两者是同一个 cp_task）
     */
    private Long taskId;

    /**
     * 是否已有 Brief 记录（false＝从未保存过，其余字段为 null）
     */
    private Boolean configured;

    /**
     * 品牌调性
     */
    private String brandTone;

    /**
     * 必显信息，一行一条
     */
    private String mustShow;

    /**
     * 禁用词与合规红线，一行一条
     */
    private String forbiddenWords;

    /**
     * 目标人群
     */
    private String targetAudience;

    /**
     * 主推卖点与优先级，一行一条
     */
    private String mainPush;

    /**
     * 尺寸/规范要求
     */
    private String sizeSpecReq;

    /**
     * 参考风格
     */
    private String styleRef;

    /**
     * 参考风格图片：任务附件 file_id 逗号分隔（原样回传，便于前端判断"有没有改动"）
     */
    private String styleRefFiles;

    /**
     * 参考风格图片明细（读的时候把 file_id 解析成文件名，页面直接显示缩略图 + 文件名）
     *
     * <p>为什么要给明细：只给一串 id，页面还得再查一次附件列表才能显示；
     * 而且附件被删掉时能在这里看出来（解析不到的会跳过，数量对不上就是线索）。</p>
     */
    private List<BriefImage> styleRefImages;

    /**
     * 参考风格图片明细项。
     *
     * @param fileId   附件ID
     * @param fileName 文件名（带扩展名）
     */
    public record BriefImage(Long fileId, String fileName) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * 状态（DRAFT/CONFIRMED）
     */
    private String status;

    /**
     * 确认人
     */
    private Long confirmedBy;

    /**
     * 确认人昵称（按 confirmedBy 查出来的，页面徽标直接展示「谁 · 何时」）
     *
     * <p>为什么不只给 ID：页面上显示一串雪花ID等于没有信息。这里在读取时解析昵称，
     * 而不是在确认时把昵称写进库——昵称会改，落库的昵称迟早是旧的。</p>
     */
    private String confirmedByName;

    /**
     * 确认时间
     */
    private LocalDateTime confirmedAt;

    /**
     * 备注
     */
    private String remark;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
