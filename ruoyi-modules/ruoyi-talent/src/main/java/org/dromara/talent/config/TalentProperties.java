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

}
