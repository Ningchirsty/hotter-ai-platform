package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.creative.domain.DpLayoutTemplate;
import org.dromara.creative.domain.vo.DpLayoutTemplateVo;
import org.dromara.creative.helper.RendererClient;
import org.dromara.creative.mapper.DpLayoutTemplateMapper;
import org.dromara.creative.service.ICreativeTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 视觉模板服务实现。
 *
 * @author creative
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeTemplateServiceImpl implements ICreativeTemplateService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_RETIRED = "RETIRED";

    /**
     * 内置模板的默认类型（按编码约定；后续接更多模板时在这里补，或由治理台维护）
     */
    private static final Map<String, String> TYPE_BY_CODE = Map.of(
        "HERO-H02", "COVER",
        "longpage", "PAGE"
    );

    private static final Map<String, String> NAME_BY_CODE = Map.of(
        "HERO-H02", "主图模板 H02",
        "longpage", "详情页长图模板"
    );

    private final DpLayoutTemplateMapper templateMapper;
    private final RendererClient rendererClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DpLayoutTemplateVo> sync() {
        List<RendererClient.RendererTemplate> remote;
        try {
            remote = rendererClient.templates();
        } catch (Exception e) {
            throw new ServiceException("渲染服务不可达，无法对账模板：" + e.getMessage());
        }
        for (RendererClient.RendererTemplate item : remote) {
            DpLayoutTemplate existing = findEntity(item.templateCode(), item.templateVersion());
            String checksum = item.checksum();
            if (existing == null) {
                DpLayoutTemplate row = new DpLayoutTemplate();
                row.setTemplateCode(item.templateCode());
                row.setVersion(item.templateVersion());
                row.setTemplateName(NAME_BY_CODE.getOrDefault(item.templateCode(), item.templateCode()));
                row.setTemplateType(TYPE_BY_CODE.getOrDefault(item.templateCode(), "PAGE"));
                row.setHtmlTemplateKey(item.templateCode() + "/" + item.templateVersion());
                row.setSchemaJson(schema(checksum, item.bytes(), null));
                row.setStatus(STATUS_DRAFT);
                row.setEnabled("0");
                row.setRemark("由渲染服务对账自动登记，待发布");
                templateMapper.insert(row);
                log.info("登记新模板 {}@{} checksum={}", item.templateCode(), item.templateVersion(), checksum);
            } else {
                String registered = checksumOf(existing);
                if (!StringUtils.equals(checksum, registered)) {
                    // 模板文件被换过：退回草稿并要求重新发布。宁可让人点一次发布，
                    // 也不要让「同一版本渲染出不同结果」悄悄发生。
                    existing.setSchemaJson(schema(checksum, item.bytes(), registered));
                    existing.setStatus(STATUS_DRAFT);
                    existing.setRemark("检测到模板文件已变化（校验和 " + shortOf(registered) + " → "
                        + shortOf(checksum) + "），已退回草稿待重新发布");
                    templateMapper.updateById(existing);
                    log.warn("模板 {}@{} 校验和变化，已退回草稿", item.templateCode(), item.templateVersion());
                }
            }
        }
        return list();
    }

    @Override
    public List<DpLayoutTemplateVo> list() {
        List<DpLayoutTemplate> rows = templateMapper.selectList(new LambdaQueryWrapper<DpLayoutTemplate>()
            .orderByAsc(DpLayoutTemplate::getTemplateCode)
            .orderByAsc(DpLayoutTemplate::getVersion));
        Map<String, RendererClient.RendererTemplate> remote = remoteMap();
        List<DpLayoutTemplateVo> list = new ArrayList<>();
        for (DpLayoutTemplate row : rows) {
            list.add(toVo(row, remote));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpLayoutTemplateVo publish(Long templateId) {
        DpLayoutTemplate row = templateMapper.selectById(templateId);
        if (row == null) {
            throw new ServiceException("模板不存在：" + templateId);
        }
        RendererClient.RendererTemplate live = remoteMap().get(key(row.getTemplateCode(), row.getVersion()));
        if (live == null) {
            throw new ServiceException("渲染服务里没有这个模板，不能发布："
                + row.getTemplateCode() + "@" + row.getVersion());
        }
        String registered = checksumOf(row);
        if (!StringUtils.equals(live.checksum(), registered)) {
            throw new ServiceException("模板校验和与渲染服务不一致，不能发布（登记 "
                + shortOf(registered) + "，实际 " + shortOf(live.checksum()) + "）：请先对账");
        }
        row.setStatus(STATUS_PUBLISHED);
        row.setEnabled("0");
        row.setRemark("已发布；校验和与渲染服务一致");
        templateMapper.updateById(row);
        return toVo(row, remoteMap());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpLayoutTemplateVo retire(Long templateId) {
        DpLayoutTemplate row = templateMapper.selectById(templateId);
        if (row == null) {
            throw new ServiceException("模板不存在：" + templateId);
        }
        row.setStatus(STATUS_RETIRED);
        row.setEnabled("1");
        templateMapper.updateById(row);
        return toVo(row, remoteMap());
    }

    @Override
    public DpLayoutTemplateVo requirePublished(String templateCode, String templateVersion) {
        DpLayoutTemplate row = findEntity(templateCode, templateVersion);
        if (row == null) {
            throw new ServiceException("模板未登记：" + templateCode + "@" + templateVersion
                + "（请先到「视觉模板库」对账）");
        }
        if (!STATUS_PUBLISHED.equals(row.getStatus())) {
            throw new ServiceException("模板未发布，不能用于排版：" + templateCode + "@" + templateVersion
                + "（当前状态 " + row.getStatus() + "）");
        }
        RendererClient.RendererTemplate live = remoteMap().get(key(templateCode, templateVersion));
        if (live == null) {
            throw new ServiceException("渲染服务里找不到模板：" + templateCode + "@" + templateVersion);
        }
        if (!StringUtils.equals(live.checksum(), checksumOf(row))) {
            // 发布后模板又被换过：这次渲染必须拒绝，避免同版本产出一致性无从谈起
            throw new ServiceException("模板文件在发布后发生变化，已拒绝使用（"
                + templateCode + "@" + templateVersion + "）；请重新对账并发布");
        }
        return toVo(row, remoteMap());
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private Map<String, RendererClient.RendererTemplate> remoteMap() {
        Map<String, RendererClient.RendererTemplate> map = new HashMap<>();
        try {
            for (RendererClient.RendererTemplate item : rendererClient.templates()) {
                map.put(key(item.templateCode(), item.templateVersion()), item);
            }
        } catch (Exception e) {
            log.warn("渲染服务不可达，模板对账降级：{}", e.getMessage());
        }
        return map;
    }

    private DpLayoutTemplate findEntity(String code, String version) {
        List<DpLayoutTemplate> rows = templateMapper.selectList(new LambdaQueryWrapper<DpLayoutTemplate>()
            .eq(DpLayoutTemplate::getTemplateCode, code)
            .eq(DpLayoutTemplate::getVersion, version)
            .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String schema(String checksum, long bytes, String previousChecksum) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("checksum", checksum);
        node.put("bytes", bytes);
        node.put("source", "builtin");
        if (previousChecksum != null) {
            node.put("previousChecksum", previousChecksum);
        }
        return node.toString();
    }

    private static String checksumOf(DpLayoutTemplate row) {
        if (StringUtils.isBlank(row.getSchemaJson())) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(row.getSchemaJson());
            return node.path("checksum").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String shortOf(String checksum) {
        return checksum == null ? "(无)" : checksum.substring(0, Math.min(8, checksum.length()));
    }

    private static String key(String code, String version) {
        return code + "@" + version;
    }

    private DpLayoutTemplateVo toVo(DpLayoutTemplate row, Map<String, RendererClient.RendererTemplate> remote) {
        DpLayoutTemplateVo vo = new DpLayoutTemplateVo();
        vo.setId(row.getId());
        vo.setTemplateCode(row.getTemplateCode());
        vo.setTemplateName(row.getTemplateName());
        vo.setTemplateType(row.getTemplateType());
        vo.setVersion(row.getVersion());
        vo.setHtmlTemplateKey(row.getHtmlTemplateKey());
        vo.setStatus(row.getStatus());
        vo.setStatusDesc(statusDesc(row.getStatus()));
        vo.setEnabled(row.getEnabled());
        vo.setRemark(row.getRemark());
        String registered = checksumOf(row);
        vo.setRegisteredChecksum(registered);
        RendererClient.RendererTemplate live = remote.get(key(row.getTemplateCode(), row.getVersion()));
        vo.setRendererAvailable(!remote.isEmpty());
        if (live != null) {
            vo.setRendererChecksum(live.checksum());
            vo.setChecksumMatches(StringUtils.equals(live.checksum(), registered));
            vo.setTemplateBytes(live.bytes());
        }
        return vo;
    }

    private static String statusDesc(String status) {
        return switch (StringUtils.blankToDefault(status, "")) {
            case STATUS_DRAFT -> "草稿";
            case STATUS_PUBLISHED -> "已发布";
            case STATUS_RETIRED -> "已退役";
            default -> status;
        };
    }

}
