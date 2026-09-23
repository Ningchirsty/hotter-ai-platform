package org.dromara.creative.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分镜单屏编辑表单。
 *
 * <p>用扁平字段而不是直接收 spec json：页面每个输入框对应一个明确字段，
 * 服务端统一组装规格——避免前端塞进任意 json 把规格结构绕过去。</p>
 *
 * @author creative
 */
@Data
public class CreativeScreenBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 屏ID
     */
    private Long id;

    /**
     * 标题
     */
    @Size(max = 255, message = "标题不能超过 255")
    private String title;

    /**
     * 副标题
     */
    @Size(max = 255, message = "副标题不能超过 255")
    private String subtitle;

    /**
     * 正文
     */
    @Size(max = 1000, message = "正文不能超过 1000")
    private String bodyText;

    /**
     * 画面独白
     */
    @Size(max = 1000, message = "画面独白不能超过 1000")
    private String pictureSoloStatement;

    /**
     * 镜头
     */
    @Size(max = 64, message = "镜头不能超过 64")
    private String shot;

    /**
     * 构图
     */
    @Size(max = 128, message = "构图不能超过 128")
    private String composition;

    /**
     * 光线
     */
    @Size(max = 128, message = "光线不能超过 128")
    private String lighting;

    /**
     * 背景
     */
    @Size(max = 128, message = "背景不能超过 128")
    private String background;

    /**
     * 出图能力编码
     */
    @Size(max = 64, message = "出图能力编码不能超过 64")
    private String workflowCode;

    /**
     * 产品保真等级（STRICT/LOOSE）
     */
    private String productLockLevel;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注不能超过 500")
    private String remark;

}
