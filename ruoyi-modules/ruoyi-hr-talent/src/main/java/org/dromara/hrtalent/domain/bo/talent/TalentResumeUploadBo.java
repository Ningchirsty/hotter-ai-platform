package org.dromara.hrtalent.domain.bo.talent;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才简历上传业务对象（SPEC-P4 §2.1 / 设计文档 §8.13）。
 *
 * <p><b>版本语义</b>：上传接口<b>只新增版本，绝不覆盖旧文件</b>；
 * 版本号由服务端计算（同一人才 {@code version_no} 单调递增），
 * 新版本自动成为当前版本，旧版本 {@code current_flag} 置否。</p>
 *
 * <p><b>不接收</b>的字段：{@code oss_id}、{@code version_no}、{@code file_hash}、{@code current_flag}
 * 全部由服务端生成，避免前端伪造对象键或复用他人文件哈希。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentResumeUploadBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 简历来源（upload/import/mail/application 等稳定编码，为空时按 upload 处理）
     */
    private String sourceType;

    /**
     * 备注（最长 500，与 DDL {@code remark varchar(500)} 一致）
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
