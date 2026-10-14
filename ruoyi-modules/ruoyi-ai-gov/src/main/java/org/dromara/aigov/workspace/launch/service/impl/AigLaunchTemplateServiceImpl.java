package org.dromara.aigov.workspace.launch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.workspace.launch.domain.AigLaunchTemplate;
import org.dromara.aigov.workspace.launch.domain.bo.AigLaunchRequestBo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchTemplateVo;
import org.dromara.aigov.workspace.launch.mapper.AigLaunchTemplateMapper;
import org.dromara.aigov.workspace.launch.service.IAigLaunchTemplateService;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 启动任务模板服务实现（设计 D11）。
 *
 * <p>解析规则与"为什么数据等级以服务端为准"见 {@link IAigLaunchTemplateService}。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigLaunchTemplateServiceImpl implements IAigLaunchTemplateService {

    private static final String YES = "Y";

    private final AigLaunchTemplateMapper templateMapper;

    private final JsonMapper jsonMapper;

    @Override
    public AigLaunchTemplate resolve(String roleCode, String actionCode) {
        if (StringUtils.isBlank(roleCode)) {
            return null;
        }
        List<AigLaunchTemplate> candidates = templateMapper.selectList(
            new LambdaQueryWrapper<AigLaunchTemplate>()
                .eq(AigLaunchTemplate::getRoleCode, roleCode.trim())
                .eq(AigLaunchTemplate::getEnabled, YES)
                .eq(AigLaunchTemplate::getStatus, "0"));
        return pick(candidates, actionCode);
    }

    @Override
    public boolean apply(AigLaunchRequestBo bo) {
        if (bo == null) {
            return false;
        }
        AigLaunchTemplate template = resolve(bo.getRoleCode(), bo.getActionCode());
        if (template == null) {
            return false;
        }
        if (StringUtils.isNotBlank(template.getTaskType())) {
            bo.setTaskType(template.getTaskType().trim());
        }
        if (StringUtils.isNotBlank(template.getProjectType())) {
            bo.setProjectType(template.getProjectType().trim());
        }
        // 数据处理范围是服务端结论，不允许被请求放宽：模板给了值就以模板为准
        if (StringUtils.isNotBlank(template.getDataLevel())) {
            if (AigDataLevelEnum.find(template.getDataLevel()) == null) {
                log.warn("模板数据等级非法，跳过覆盖：templateCode={} dataLevel={}",
                    template.getTemplateCode(), template.getDataLevel());
            } else {
                if (StringUtils.isNotBlank(bo.getDataLevel())
                    && !template.getDataLevel().trim().equalsIgnoreCase(bo.getDataLevel().trim())) {
                    log.info("启动数据等级以服务端模板为准：role={} action={} 请求={} 采纳={}",
                        bo.getRoleCode(), bo.getActionCode(), bo.getDataLevel(), template.getDataLevel());
                }
                bo.setDataLevel(template.getDataLevel().trim());
            }
        }
        // 输入快照：员工只填业务字段，快照由服务端按业务输入组装
        if (YES.equalsIgnoreCase(template.getSnapshotFromContext())
            && StringUtils.isBlank(bo.getSnapshotJson())
            && bo.getContext() != null && !bo.getContext().isEmpty()) {
            try {
                bo.setSnapshotJson(jsonMapper.writeValueAsString(bo.getContext()));
            } catch (Exception e) {
                log.warn("按模板组装输入快照失败（不影响其它字段）：templateCode={}",
                    template.getTemplateCode(), e);
            }
        }
        return true;
    }

    @Override
    public AigLaunchTemplateVo describe(String roleCode, String actionCode) {
        AigLaunchTemplate template = resolve(roleCode, actionCode);
        return template == null ? null : toVo(template);
    }

    /**
     * 在候选模板里挑一个：精确 {@code action_code} 优先；否则取该岗位的默认模板（action 为空）。
     *
     * <p>抽成静态纯函数是为了能直接单测"精确优先、默认兜底、都没有则不套用"这三条。</p>
     *
     * @param candidates 候选（同岗位、已启用）
     * @param actionCode 目标卡片编码
     * @return 选中的模板；没有匹配返回 null
     */
    static AigLaunchTemplate pick(List<AigLaunchTemplate> candidates, String actionCode) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        String wanted = actionCode == null ? null : actionCode.trim();
        AigLaunchTemplate fallback = null;
        for (AigLaunchTemplate candidate : candidates) {
            String code = candidate.getActionCode() == null ? null : candidate.getActionCode().trim();
            if (StringUtils.isNotBlank(wanted) && wanted.equals(code)) {
                return candidate;
            }
            if (StringUtils.isBlank(code)) {
                fallback = candidate;
            }
        }
        return fallback;
    }

    /**
     * 实体 → 视图。
     *
     * @param template 实体
     * @return 视图
     */
    static AigLaunchTemplateVo toVo(AigLaunchTemplate template) {
        AigLaunchTemplateVo vo = new AigLaunchTemplateVo();
        vo.setTemplateCode(template.getTemplateCode());
        vo.setTitle(template.getTitle());
        vo.setRoleCode(template.getRoleCode());
        vo.setActionCode(template.getActionCode());
        vo.setTaskType(template.getTaskType());
        vo.setProjectType(template.getProjectType());
        vo.setDataLevel(template.getDataLevel());
        vo.setSnapshotFromContext(template.getSnapshotFromContext() == null
            ? Boolean.TRUE : YES.equalsIgnoreCase(template.getSnapshotFromContext()));
        if (StringUtils.isNotBlank(template.getBusinessFields())) {
            for (String field : template.getBusinessFields().split(",")) {
                if (StringUtils.isNotBlank(field)) {
                    vo.getBusinessFields().add(field.trim());
                }
            }
        }
        return vo;
    }

}
