package org.dromara.talent.constant;

import java.util.List;

/**
 * 集团人才库常量定义
 *
 * @author talent
 */
public interface TalentConstants {

    String PERM_PROFILE_LIST = "talent:profile:list";
    String PERM_PROFILE_QUERY = "talent:profile:query";
    String PERM_PROFILE_ADD = "talent:profile:add";
    String PERM_PROFILE_EDIT = "talent:profile:edit";
    String PERM_PROFILE_ARCHIVE = "talent:profile:archive";
    String PERM_PROFILE_PHONE = "talent:profile:phone";
    String PERM_PROFILE_GRANT = "talent:profile:grant";
    String PERM_PROFILE_IMPORT = "talent:profile:import";
    String PERM_ATTACH_MANAGE = "talent:attachment:manage";
    String PERM_ATTACH_UPLOAD = "talent:attachment:upload";
    String PERM_ATTACH_DOWNLOAD = "talent:attachment:download";
    String PERM_DUP_VIEW = "talent:duplicate:view";
    String PERM_DUP_CONFIRM = "talent:duplicate:confirm";
    String PERM_PARSE_VIEW = "talent:parse:view";
    String PERM_PARSE_CONFIRM = "talent:parse:confirm";
    String PERM_PARSE_RETRY = "talent:parse:retry";
    String PERM_EXPORT_CREATE = "talent:export:create";
    String PERM_EXPORT_DOWNLOAD = "talent:export:download";
    String PERM_AUDIT_LIST = "talent:audit:list";

    String OBJECT_KEY_ROOT = "talent-private";
    String KEY_SEG_RESUMES = "resumes";
    String KEY_SEG_ATTACHMENTS = "attachments";
    String KEY_SEG_EXPORTS = "exports";
    String KEY_SEG_QUARANTINE = "quarantine";

    /**
     * 允许的附件扩展名（小写，无点）
     */
    List<String> ALLOWED_EXT = List.of("pdf", "docx", "doc", "jpg", "jpeg", "png");
    /**
     * 单文件上限 25MB
     */
    long MAX_FILE_SIZE = 25L * 1024 * 1024;
    /**
     * 导出文件有效期（小时）
     */
    int EXPORT_EXPIRE_HOURS = 24;
    /**
     * 人才编号前缀
     */
    String TALENT_NO_PREFIX = "TL";

    /**
     * 本地规则抽取器版本号（导入简历直接建档的留痕标识）
     */
    String PARSER_VERSION_LOCAL = "local-rule-v1";

}
