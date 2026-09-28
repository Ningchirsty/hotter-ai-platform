package org.dromara.content.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 品牌 Brief 保存表单（upsert）。
 *
 * <p><b>刻意没有 status 字段</b>：状态只能由 {@code POST .../brand-brief/confirm} 推进。
 * 如果保存接口能收 status，前端一次误传就能把「品牌方已确认的要求」改回草稿（或反过来），
 * 而视觉门的闸门项「品牌 Brief 已填写并确认」判据正是这个值——那等于把闸门的钥匙交给了表单。</p>
 *
 * <p>多行字段（必显信息/禁用词/主推卖点）用 {@code \n} 分隔，服务端原样存字符串，不拆成数组：
 * 拆分规则（顿号？换行？分号？）只要两端各写一份就一定会不一致。</p>
 *
 * @author content
 */
@Data
public class BrandBriefBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 品牌调性
     */
    @Size(max = 500, message = "品牌调性不能超过 500")
    private String brandTone;

    /**
     * 必显信息，一行一条
     */
    @Size(max = 2000, message = "必显信息不能超过 2000")
    private String mustShow;

    /**
     * 禁用词与合规红线，一行一条
     */
    @Size(max = 1000, message = "禁用词不能超过 1000")
    private String forbiddenWords;

    /**
     * 目标人群
     */
    @Size(max = 500, message = "目标人群不能超过 500")
    private String targetAudience;

    /**
     * 主推卖点与优先级，一行一条
     */
    @Size(max = 2000, message = "主推卖点不能超过 2000")
    private String mainPush;

    /**
     * 尺寸/规范要求
     */
    @Size(max = 1000, message = "尺寸规范要求不能超过 1000")
    private String sizeSpecReq;

    /**
     * 参考风格
     */
    @Size(max = 1000, message = "参考风格不能超过 1000")
    private String styleRef;

    /**
     * 参考风格图片：任务附件 file_id 逗号分隔（前端上传拿到 id 后拼成串一次提交）
     *
     * <p>服务端会逐个校验：附件存在、属于本任务、且是图片；非法 id 直接拒绝并说明是哪一个，
     * 不静默丢弃（否则页面显示 3 张、库里只存了 2 张，谁也查不出来）。</p>
     */
    @Size(max = 1000, message = "参考风格图片过多（fileId 串不能超过 1000）")
    private String styleRefFiles;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注不能超过 500")
    private String remark;

}
