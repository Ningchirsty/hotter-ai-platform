package org.dromara.hrtalent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 招聘与人才管理一体化系统业务配置。
 * <p>配置前缀 {@code hrtalent}，对应 {@code hr-talent.yml}（设计文档 §17.3）。
 * 该文件被 {@code application.yml} 通过 {@code spring.config.import} 引入。</p>
 *
 * <p>安全约定：本类中的 {@link #phoneHashSalt} 属密钥类配置，生产必须由环境变量
 * 或安全配置中心注入，禁止提交真实值到代码库；日志中禁止输出本类字段的实际取值。</p>
 *
 * @author hr-talent
 */
@Data
@Component
@ConfigurationProperties(prefix = "hrtalent")
public class HrTalentProperties {

    /**
     * 附件允许的扩展名列表（小写，不含点）。
     */
    private List<String> attachmentAllowExtensions = List.of(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf",
        "jpg", "jpeg", "png", "gif", "zip", "7z");

    /**
     * 单文件大小上限（超出时返回 HR_RESUME_001）。
     */
    private DataSize attachmentMaxSize = DataSize.ofMegabytes(20);

    /**
     * 单条业务记录允许的附件数量上限。
     */
    private int attachmentMaxCount = 20;

    /**
     * 预签名下载地址有效期（短时，禁止落库或写日志）。
     */
    private Duration presignTtl = Duration.ofMinutes(5);

    /**
     * 导出文件有效期，超期由定时任务清理。
     */
    private Duration exportValidDuration = Duration.ofDays(7);

    /**
     * 单次导出最大行数。
     */
    private int exportMaxRows = 10000;

    /**
     * 单次导入批次最大行数。
     */
    private int importMaxRows = 5000;

    /**
     * 人才附件专用 OSS 配置键；为空时使用 {@code OssFactory} 默认配置。
     */
    private String ossConfigKey;

    /**
     * 简历解析开关；未取得个人信息处理批准前必须保持 {@code false}。
     */
    private boolean resumeParseEnabled = false;

    /**
     * 简历解析器版本标识，写入解析任务用于结果溯源。
     */
    private String resumeParserVersion = "hr-resume-parser/1.0.0";

    /**
     * 电话/邮箱不可逆哈希盐；生产由密钥管理提供。
     */
    private String phoneHashSalt;

    /**
     * 归一化后的附件扩展名集合（去点、去空白、转小写，保持插入顺序）。
     *
     * @return 扩展名集合（不为 null）
     */
    public Set<String> attachmentAllowExtensionSet() {
        Set<String> extensions = new LinkedHashSet<>();
        if (attachmentAllowExtensions == null) {
            return extensions;
        }
        for (String extension : attachmentAllowExtensions) {
            if (extension == null || extension.isBlank()) {
                continue;
            }
            extensions.add(extension.trim().toLowerCase(Locale.ROOT).replace(".", ""));
        }
        return extensions;
    }

    /**
     * 判断扩展名是否在允许列表内。
     *
     * @param extension 扩展名（可带点，可为 null）
     * @return 是否允许
     */
    public boolean isAttachmentExtensionAllowed(String extension) {
        if (extension == null || extension.isBlank()) {
            return false;
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT).replace(".", "");
        return attachmentAllowExtensionSet().contains(normalized);
    }

}
