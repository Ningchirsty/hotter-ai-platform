package org.dromara.creative.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 交付产物清单 dp_delivery_artifact（V0.2 R30，文档 §26 Renderer Hub）。
 *
 * <p><b>存的是清单，不是字节</b>：产物本身已经是任务附件（多图交付）或详情页版本长图，
 * 再存一份等于交付一次多占一份存储。清单里记着每张图的附件ID、尺寸、字节数与 sha256，
 * 交付包（ZIP）在下载时按清单现拼。</p>
 *
 * <p><b>版本口径</b>：{@code (task_id, renderer, version)} 唯一，版本号由服务层递增——
 * "重新交付一次"就是新版本，历史版本留着可回溯（与详情页版本同一套思路）。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_delivery_artifact")
public class DpDeliveryArtifact extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 视觉项目（cp_task.task_id）
     */
    private Long taskId;

    /**
     * 交付类型（ECOM_DETAIL / MAIN_IMAGE …）
     */
    private String deliveryType;

    /**
     * 渲染器编码（LONG_PAGE / MULTI_IMAGE …）
     */
    private String renderer;

    /**
     * 该项目该渲染器下的第几版
     */
    private Integer version;

    /**
     * 交付清单 JSON（schema=delivery-manifest/1）
     */
    private String manifestJson;

    /**
     * 产物张数（长图为 1）
     */
    private Integer imageCount;

    /**
     * 产物字节合计
     */
    private Long totalBytes;

    /**
     * 清单校验和
     */
    private String checksum;

    /**
     * 备注（缺图屏等如实记录）
     */
    private String remark;

    @TableLogic
    private String delFlag;
}
