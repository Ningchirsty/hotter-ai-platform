package org.dromara.aigov.workspace.portal.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资产聚合索引 {@code aig_asset_index}（增量 11）。
 *
 * <p><b>字段级口径以 {@code script/sql/aig_asset_index.sql} 的列注释为准</b>。</p>
 *
 * <p><b>它是一张派生缓存，不是事实来源</b>：内容来自各域的 {@code MyAssetPort}
 * （各域保证归属过滤），本表只负责"放到一起按时间排序分页"。因此它**物理删除**
 * （重建时按用户+域先删后插），没有 {@code del_flag}——逻辑删除会让旧行占着唯一键。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_asset_index")
public class AigAssetIndex extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "index_id")
    private Long indexId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 域编码（IMAGE / VIDEO / CONTENT）
     */
    private String domain;

    /**
     * 资产ID（各域自己的主键）
     */
    private Long assetId;

    /**
     * 资产类型（各域口径）
     */
    private String assetType;

    /**
     * 来源（UPLOAD/OUTPUT/REFERENCE…）
     */
    private String sourceKind;

    /**
     * 展示名
     */
    private String name;

    /**
     * MIME（内容域不存，留空）
     */
    private String mimeType;

    /**
     * 字节数
     */
    private Long sizeBytes;

    /**
     * 产出/所属任务ID
     */
    private Long taskId;

    /**
     * 资产的创建时间（排序键，来自各域；与 {@code createTime} 不同）
     */
    private LocalDateTime assetTime;

    /**
     * 备注
     */
    private String remark;

}
