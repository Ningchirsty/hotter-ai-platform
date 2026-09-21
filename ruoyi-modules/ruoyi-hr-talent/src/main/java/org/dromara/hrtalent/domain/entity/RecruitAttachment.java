package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 招聘业务附件对象 hr_recruit_attachment（设计文档 §8.8 / §9.4 / §15.3）。
 *
 * <p><b>存储约定</b>：{@link #ossId} 只保存对象存储的<b>对象标识（对象键）</b>，
 * <b>不保存</b>永久公共下载地址；需要访问时由服务端鉴权后流式读取，
 * 或按需生成短时预签名地址（禁止落库、禁止写日志）。</p>
 *
 * <p><b>版本约定</b>：同一业务对象（{@code biz_type + biz_id}）同一 {@link #fileType} 的新文件
 * 新增版本（{@link #versionNo} 递增），旧版本 {@link #currentFlag} 置 {@code 0}，
 * <b>不覆盖</b>旧文件（§9.4）。</p>
 *
 * <p><b>邮箱/简历约定</b>：简历使用独立的 {@code hr_talent_resume} 版本表（P4），
 * 通用附件表<b>不重复保存</b>简历业务记录（§8.8）。</p>
 *
 * <p>字段与 {@code script/sql/hr_recruit.sql} 的建表语句逐列一致；主键列为 {@code attachment_id}。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_attachment")
public class RecruitAttachment extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 附件ID（主键）
     */
    @TableId(value = "attachment_id")
    private Long attachmentId;

    /**
     * 业务类型（application/interview/background/offer/talent等稳定编码）
     */
    private String bizType;

    /**
     * 业务对象ID
     */
    private Long bizId;

    /**
     * 附件类型（resume/portfolio/interview/background/offer/other，字典 recruit_attachment_type）
     */
    private String fileType;

    /**
     * OSS 对象ID（对象键，受控访问，不存长期公网URL）
     */
    private String ossId;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 文件后缀（小写、不含点）
     */
    private String fileSuffix;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件哈希（SHA-256 十六进制，用于重复文件提示，非唯一约束）
     */
    private String fileHash;

    /**
     * 附件版本号（非负整数，新文件新增版本不覆盖旧文件）
     */
    private Integer versionNo;

    /**
     * 是否当前版本（0否 1是）
     */
    private String currentFlag;

    /**
     * 安全级别（字典 recruit_data_level）
     */
    private String securityLevel;

    /**
     * 上传人用户ID
     */
    private Long uploadedBy;

    /**
     * 上传时间
     */
    private LocalDateTime uploadedTime;

    /**
     * 删除标志（0代表存在 1代表删除；删除采用逻辑删除与保留期策略，不物理删除文件）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
