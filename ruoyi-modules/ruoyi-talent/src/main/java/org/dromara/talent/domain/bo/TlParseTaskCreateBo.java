package org.dromara.talent.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.talent.domain.TlParseTask;

import java.io.Serial;
import java.io.Serializable;

/**
 * 简历解析任务创建业务对象
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlParseTask.class, reverseConvertGenerate = false)
public class TlParseTaskCreateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 附件ID
     */
    @NotNull(message = "附件ID不能为空", groups = {AddGroup.class})
    private Long attachmentId;

}
