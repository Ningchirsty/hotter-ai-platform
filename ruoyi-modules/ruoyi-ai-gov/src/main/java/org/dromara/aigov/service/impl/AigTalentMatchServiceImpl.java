package org.dromara.aigov.service.impl;

import cn.hutool.core.collection.CollUtil;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.service.IAigTalentMatchService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code talent_match} 本地规则匹配实现。
 * <p>做法：需求技能标签与候选技能标签求交集，按命中率打分（命中数 / 需求数），
 * 降序取前 {@value #MAX_RESULT} 条。全程本地计算，<b>不联网、不外发</b>。</p>
 * <p><b>隐私口径</b>：输出只含候选编号与技能标签，<b>绝不回显候选人姓名/联系方式</b>；
 * 结论一律带 {@code pendingConfirm}，不得自动流转。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
public class AigTalentMatchServiceImpl implements IAigTalentMatchService {

    /**
     * 最多返回的候选数。
     */
    private static final int MAX_RESULT = 20;

    /**
     * 最多处理的候选数（防御超大载荷）。
     */
    private static final int MAX_INPUT_CANDIDATES = 200;

    /**
     * 分数精度。
     */
    private static final int SCORE_SCALE = 4;

    @Override
    public String match(Map<String, Object> payload) {
        Set<String> demandSkills = readSkills(payload, "skillTags", "skills", "demandSkills");
        List<Map<String, Object>> candidates = readCandidates(payload);
        List<Map<String, Object>> scored = new ArrayList<>();
        BigDecimal topScore = BigDecimal.ZERO;
        for (int i = 0; i < candidates.size(); i++) {
            Map<String, Object> candidate = candidates.get(i);
            if (candidate == null) {
                continue;
            }
            Set<String> candidateSkills = readSkills(candidate, "skills", "skillTags", "tags");
            Set<String> matched = new LinkedHashSet<>();
            Set<String> missing = new LinkedHashSet<>(demandSkills);
            for (String skill : candidateSkills) {
                if (demandSkills.contains(skill)) {
                    matched.add(skill);
                    missing.remove(skill);
                }
            }
            BigDecimal score = calcScore(matched.size(), demandSkills.size());
            if (score.compareTo(topScore) > 0) {
                topScore = score;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            // 只回显编号，不回显姓名等个人信息
            row.put("candidateId", StringUtils.blankToDefault(readString(candidate, "candidateId", "id"), "index-" + i));
            row.put("matchedSkills", new ArrayList<>(matched));
            row.put("missingSkills", new ArrayList<>(missing));
            row.put("score", score);
            row.put("evidence", "命中 " + matched.size() + "/" + demandSkills.size() + " 项需求技能");
            scored.add(row);
        }
        scored.sort(Comparator
            .comparing((Map<String, Object> row) -> (BigDecimal) row.get("score")).reversed()
            .thenComparing(row -> String.valueOf(row.get("candidateId"))));
        List<Map<String, Object>> top = scored.size() > MAX_RESULT ? scored.subList(0, MAX_RESULT) : scored;

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("capabilityCode", AigConstants.CAP_TALENT_MATCH);
        output.put("candidates", top);
        output.put("score", topScore);
        output.put("candidateCount", scored.size());
        output.put("evidence", "本地规则匹配：需求技能 " + demandSkills.size() + " 项，候选 "
            + scored.size() + " 个，最高命中率 " + topScore.toPlainString());
        // 结论不得自动流转：必须人工确认
        output.put("pendingConfirm", List.of("最终候选与资源协调必须由项目负责人/人才库责任人确认"));
        return JsonUtils.toJsonString(output);
    }

    /**
     * 计算命中率得分。
     *
     * @param matched 命中技能数
     * @param total   需求技能总数
     * @return 0~1 之间的得分（保留 4 位）
     */
    private BigDecimal calcScore(int matched, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(matched)
            .divide(BigDecimal.valueOf(total), SCORE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 读取候选列表。
     *
     * @param payload 载荷
     * @return 候选列表（不为 null，已截断）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readCandidates(Map<String, Object> payload) {
        if (payload == null) {
            return List.of();
        }
        Object raw = payload.get("candidates");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (result.size() >= MAX_INPUT_CANDIDATES) {
                break;
            }
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    /**
     * 读取技能标签集合，兼容字符串数组与逗号分隔字符串。
     *
     * @param source 数据源
     * @param keys   候选字段名（按顺序取第一个非空）
     * @return 规范化后的技能标签集合（小写、去空白）
     */
    private Set<String> readSkills(Map<String, Object> source, String... keys) {
        Set<String> skills = new LinkedHashSet<>();
        if (source == null) {
            return skills;
        }
        for (String key : keys) {
            Object raw = source.get(key);
            if (raw == null) {
                continue;
            }
            if (raw instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    addSkill(skills, item == null ? null : String.valueOf(item));
                }
            } else if (raw.getClass().isArray()) {
                for (Object item : (Object[]) raw) {
                    addSkill(skills, item == null ? null : String.valueOf(item));
                }
            } else {
                for (String item : StringUtils.split(String.valueOf(raw), ',')) {
                    addSkill(skills, item);
                }
            }
            if (CollUtil.isNotEmpty(skills)) {
                break;
            }
        }
        return skills;
    }

    /**
     * 规范化并加入技能集合。
     *
     * @param skills 集合
     * @param raw    原始技能
     */
    private void addSkill(Set<String> skills, String raw) {
        if (StringUtils.isBlank(raw)) {
            return;
        }
        skills.add(raw.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 读取字符串字段。
     *
     * @param source 数据源
     * @param keys   候选字段名
     * @return 第一个非空值，取不到返回 null
     */
    private String readString(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object raw = source.get(key);
            if (raw != null && StringUtils.isNotBlank(String.valueOf(raw))) {
                return String.valueOf(raw);
            }
        }
        return null;
    }

}
