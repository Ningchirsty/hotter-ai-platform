package org.dromara.talent.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 人才库上线安全兜底自检。
 * <p>
 * 人才库涉及个人信息（手机号、简历），部分安全能力依赖外部配置开关。
 * 这些开关默认值并不安全，一旦运维遗漏就会造成"看起来有加密、实际等同明文"的静默风险。
 * 因此启动时做一次显式自检并打印醒目的告警，让问题在启动日志里就暴露出来。
 * </p>
 * <p>
 * 本类只做告警，不阻断启动：生产环境的策略由运维决定，但必须被看见。
 * </p>
 *
 * @author talent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TalentSecurityGuard implements ApplicationRunner {

    /**
     * 框架字段加解密开关（ruoyi-common-encrypt）。
     */
    private static final String ENCRYPT_ENABLE_KEY = "mybatis-encryptor.enable";

    private final Environment environment;

    private final TalentProperties talentProperties;

    /**
     * 启动自检：逐项检查人才库依赖的安全配置，不满足时打印告警。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        boolean encryptEnabled = environment.getProperty(ENCRYPT_ENABLE_KEY, Boolean.class, false);
        if (!encryptEnabled) {
            log.error("""
                
                ============================================================
                [人才库安全告警] mybatis-encryptor.enable = false
                手机号密文字段 phone_cipher 当前不会被加密，等同于明文存储！
                上线前必须开启并配置 AES 密钥，否则不满足个人信息保护要求。
                ============================================================
                """);
        }

        String salt = talentProperties.getPhoneHashSalt();
        if (salt == null || salt.isBlank() || "change-me-in-production".equals(salt)) {
            log.error("""
                
                ============================================================
                [人才库安全告警] talent.phone-hash-salt 仍为默认值
                重复预警使用的手机号哈希可被跨库比对/彩虹表反推。
                请改为由密钥管理提供的随机盐，且各环境不得复用。
                ============================================================
                """);
        }

        if (talentProperties.isParseEnabled()) {
            log.warn("""
                
                ============================================================
                [人才库合规提醒] talent.parse-enabled = true
                简历解析已启用。请确认已取得公司关于个人信息处理方式的书面确认，
                否则不得处理真实简历（参见设计方案 §13.4）。
                ============================================================
                """);
        }

        if (!talentProperties.isVirusScanEnabled()) {
            log.warn("""
                
                [人才库提醒] talent.virus-scan-enabled = false
                附件上传后将被直接置为 CLEAN，不做病毒扫描。
                对外提供服务前请接入扫描服务或明确接受该风险。
                """);
        }

        log.info("[人才库] 安全自检完成：加密={}, 解析={}, 病毒扫描={}, 导出上限={}行",
            encryptEnabled ? "已开启" : "未开启",
            talentProperties.isParseEnabled() ? "已启用" : "未启用",
            talentProperties.isVirusScanEnabled() ? "已接入" : "未接入",
            talentProperties.getExportMaxRows());
    }

}
