package org.dromara.creative.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目素材概况 / 清理结果（V0.2 R25）。
 *
 * <p><b>为什么要有"预览 + 显式清理"这一对</b>：按用户决定，**删项目默认保留素材**
 * （软删项目只把项目置为删除，附件、生成图与对象存储里的文件都留着），
 * 因此释放空间必须是一个**显式动作**：先看清楚要删什么（本 VO 的统计字段），
 * 再二次确认（输入项目名）才真删。把"删项目"和"清素材"合成一个动作，
 * 会让人在不知情的情况下丢掉参考图与产出图——那是不可恢复的。</p>
 *
 * @author creative
 */
@Data
public class ProjectMaterialsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long taskId;

    /** 项目名（二次确认时要原样输入它） */
    private String taskName;

    /** 项目是否已被删除（软删）：删除的项目清理素材是"打扫"，未删除的是"破坏性操作" */
    private Boolean projectDeleted;

    /** 附件数量（cp_task_file） */
    private Integer fileCount = 0;

    /** 附件字节合计 */
    private Long fileBytes = 0L;

    /** 生成记录数量（dp_generation） */
    private Integer generationCount = 0;

    /** 排版版本数量（dp_detail_page_version） */
    private Integer versionCount = 0;

    // ---------------- 清理结果（purge 才有值） ----------------

    /** 是否真的执行了清理 */
    private Boolean purged;

    /** 已删除的对象数（对象存储） */
    private Integer purgedObjects = 0;

    /** 已删除的附件行数 */
    private Integer purgedFiles = 0;

    /** 已删除的生成记录数 */
    private Integer purgedGenerations = 0;

    /** 已删除的字节数（按附件表的 file_size 计） */
    private Long purgedBytes = 0L;

    /** 已删除的质检记录数（R26：它引用的两张图都被删了，留着就是指向不存在文件的行） */
    private Integer purgedChecks = 0;

    /** 项目删除时间（批量清单用） */
    private java.time.LocalDateTime deletedAt;

    /** 提示（例如"项目仍在，需要 force"） */
    private String note;
}
