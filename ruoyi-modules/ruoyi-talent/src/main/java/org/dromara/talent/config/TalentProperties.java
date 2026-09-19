package org.dromara.talent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 集团人才库业务配置
 *
 * @author talent
 */
@Data
@Component
@ConfigurationProperties(prefix = "talent")
public class TalentProperties {

    /**
     * 手机号哈希盐（必填，生产由密钥管理提供；不得硬编码）
     */
    private String phoneHashSalt = "change-me-in-production";

    /**
     * 人才附件专用 OSS 配置键；为空则用 OssFactory.instance() 默认配置
     */
    private String ossConfigKey;

    /**
     * 是否启用病毒扫描（未接入时保持 false，附件直接置 CLEAN 并记录原因）
     */
    private boolean virusScanEnabled = false;

    /**
     * 是否启用简历解析（未获批前必须 false，解析任务状态置 DISABLED）
     */
    private boolean parseEnabled = false;

    /**
     * 导出单次最大行数
     */
    private int exportMaxRows = 10000;

    /**
     * 是否启用「导入简历直接建档」（全本地 PDFBox / POI / 正则，不调用任何外部服务）。
     * <p>
     * 与 {@link #parseEnabled} 互相独立：本开关不受「解析服务需个人信息处理审批」的限制。
     */
    private boolean resumeImportEnabled = true;

    /**
     * 导入预览临时文件目录，默认 {@code ${java.io.tmpdir}/talent-import}。
     * <p>
     * 说明：Spring 不会对 Java 字段默认值做占位符解析，故此处直接解析运行时临时目录，
     * 语义与 {@code ${java.io.tmpdir}/talent-import} 完全一致；仍可通过配置项覆盖。
     */
    private String importTempDir = System.getProperty("java.io.tmpdir") + "/talent-import";

    /**
     * 导入预览临时文件有效期（分钟），超期文件在下次 preview 时顺带清理
     */
    private int importTempTtlMinutes = 30;

    /**
     * 单次导入允许抽取的简历文本最大字符数（超出部分截断，防止正则回溯型拒绝服务）
     */
    private int importMaxTextLength = 200000;

}
