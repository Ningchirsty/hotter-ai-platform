package org.dromara.asset.api.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 「我的资产」跨域只读视图（各域提供方返回，门户侧聚合展示）。
 *
 * <h3>为什么这个 DTO 由平台统一，而不是各域各回一套</h3>
 * <p>门户要把图片/视频/内容附件并排显示。如果每个域回自己的实体，聚合层就得认识三套类型
 * （并且每加一个域就改一次聚合层）。这里只放"展示一条资产需要的最小字段"，
 * <b>刻意不含</b>存储键、哈希、解析正文引用等内部事实——那些属于各域，不该因为"顺手"进入门户契约。</p>
 *
 * <p>归属过滤（"这条是不是这个人/这个任务的"）由<b>提供方实现</b>完成，不在本 DTO 上：
 * 各域的可见性规则在各自模块里，聚合层只消费已过滤的结果。</p>
 *
 * @author ai-gov
 */
@Data
public class MyAssetDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属域（IMAGE / VIDEO / CONTENT）
     */
    private String domain;

    /**
     * 资产ID（各域自己的主键：image_asset.id / video_asset.id / cp_task_file.file_id）
     */
    private Long assetId;

    /**
     * 资产类型（如 IMAGE/VIDEO/AUDIO/DESIGN…，各域口径）
     */
    private String assetType;

    /**
     * 来源（UPLOAD 上传 / OUTPUT 任务产出；内容域为 UPLOAD/REFERENCE）
     */
    private String sourceKind;

    /**
     * 展示名（原始文件名）
     */
    private String name;

    /**
     * MIME 类型
     */
    private String mimeType;

    /**
     * 字节数
     */
    private Long sizeBytes;

    /**
     * 产出该资产的任务ID（上传资产可空）
     */
    private Long taskId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
