package org.dromara.content.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务附件对象 cp_task_file
 *
 * <p>业务库只存文件引用（{@code fileRef}），不存文件本体（设计文档 §16.1）。
 * {@code parseMessage} 必须写「用户能看懂的原因」——跳过的类型不能悄悄返回空。</p>
 *
 * @author content
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cp_task_file")
public class CpTaskFile extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 附件ID
     */
    @TableId(value = "file_id")
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
     * 扩展名（小写）
     */
    private String fileExt;

    /**
     * 字节数
     */
    private Long fileSize;

    /**
     * 文件引用（对象存储键；业务库不存文件本体）
     */
    private String fileRef;

    /**
     * 文件类型（见 ContentFileKindEnum）
     */
    private String fileKind;

    /**
     * 来源（UPLOAD上传 / REFERENCE引用）
     */
    private String sourceType;

    /**
     * 该文件的数据等级（可高于任务等级）
     */
    private String dataLevel;

    /**
     * 解析状态（见 ContentParseStatusEnum）
     */
    private String parseStatus;

    /**
     * 解析失败或跳过的可读原因
     */
    private String parseMessage;

    /**
     * 抽取文本的引用（避免正文入库）
     */
    private String parsedTextRef;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

}
