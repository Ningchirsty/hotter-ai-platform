package org.dromara.aigov.agent.domain.vo;

import java.util.List;

/**
 * Package 安装结果（把声明的内容物建成 Agent/Skill 版本）。
 *
 * @param packageId        Package ID
 * @param packageVersionId Package 版本ID
 * @param alreadyInstalled 本次是否为重复调用（幂等命中：该 Package 版本已经装过了）
 * @param agents           装出来的 Agent（每项含编码、Agent ID、版本ID、是否本次新建）
 * @param skills           装出来的 Skill
 * @param note             可读说明
 * @author ai-gov
 */
public record AigPackageInstallVo(
    Long packageId,
    Long packageVersionId,
    boolean alreadyInstalled,
    List<InstalledItem> agents,
    List<InstalledItem> skills,
    String note
) {

    /**
     * 一个装出来的对象。
     *
     * @param code      编码（Agent/Skill 编码）
     * @param parentId  aig_agent.agent_id / aig_skill.skill_id
     * @param versionId aig_agent_version.agent_version_id / aig_skill_version.skill_version_id
     * @param version   版本号（与 Package 版本一致）
     * @param created   本次是否新建（false = 已存在同版本，未重复建）
     */
    public record InstalledItem(String code, Long parentId, Long versionId, String version,
                                boolean created) {
    }

}
