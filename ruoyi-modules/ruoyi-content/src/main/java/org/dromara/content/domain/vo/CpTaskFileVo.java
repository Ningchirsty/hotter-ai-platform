package org.dromara.content.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.content.domain.CpTaskFile;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务附件视图对象 cp_task_file
 *
 * @author content
 */
@Data
@AutoMapper(target = CpTaskFile.class)
public class CpTaskFileVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 附件ID
     */
    private Long fileId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 扩展名
     */
    private String fileExt;

    /**
     * 字节数
     */
    private Long fileSize;

    /**
     * 文件引用
     */
    private String fileRef;

    /**
     * 文件类型
     */
    private String fileKind;

    /**
     * 来源（UPLOAD/REFERENCE）
     */
    private String sourceType;

    /**
     * 该文件的数据等级
     */
    private String dataLevel;

    /**
     * 解析状态
     */
    private String parseStatus;

    /**
     * 解析失败或跳过的可读原因
     */
    private String parseMessage;

    /**
     * 抽取文本的引用
     */
    private String parsedTextRef;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人ID（{@code @Translation} 的取值来源，**不可省略**）
     */
    private Long createBy;

    /**
     * 创建人账号
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
