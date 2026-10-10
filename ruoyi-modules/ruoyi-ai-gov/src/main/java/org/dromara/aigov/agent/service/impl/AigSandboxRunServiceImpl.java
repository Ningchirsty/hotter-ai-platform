package org.dromara.aigov.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.domain.AigSandboxRun;
import org.dromara.aigov.agent.domain.bo.AigSandboxRunRecordBo;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.enums.AigSandboxAttestationEnum;
import org.dromara.aigov.agent.evaluation.AigSandboxRunEvidence;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.mapper.AigPackageVersionMapper;
import org.dromara.aigov.agent.mapper.AigSandboxRunMapper;
import org.dromara.aigov.agent.mapper.AigSkillVersionMapper;
import org.dromara.aigov.agent.service.IAigSandboxRunService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 沙箱运行证据实现（登记 + 查询）。
 *
 * <p><b>本类不执行任何东西</b>：它只把宿主侧 worker 的 {@code result.json} 登记入库，
 * 并按原文里的字段回答"这个版本能不能算沙箱跑通"。执行隔离在宿主侧独立进程与一次性容器里
 * （ADR-015）——平台后端没有 docker socket，也不应该有。</p>
 *
 * <p><b>为什么逐字要求执行器的字段，而不是"缺了就按默认值补"</b>：门槛要证明的是
 * "无网条件下退出码为 0"。若原文缺 {@code network} 就默认成 {@code none}，这份证据就变成
 * 了<b>由登记代码制造出来的</b>——恰好把要证明的那件事给补上了。缺字段一律拒绝登记，
 * 并明确告知"不要手写 result.json，请提交执行器原样输出"。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigSandboxRunServiceImpl implements IAigSandboxRunService {

    /**
     * 原文里必须有、且直接参与判据的字段。缺任何一个都拒绝登记（见类注释）。
     */
    private static final String[] REQUIRED_FIELDS = {"jobId", "image", "exitCode", "timedOut", "network"};

    private final AigSandboxRunMapper sandboxRunMapper;

    private final AigAgentVersionMapper agentVersionMapper;

    private final AigSkillVersionMapper skillVersionMapper;

    private final AigPackageVersionMapper packageVersionMapper;

    /**
     * 注入 {@link JsonMapper} 而不是用 {@code JsonUtils}：后者在静态初始化里
     * 直接 {@code SpringUtils.getBean(...)}，纯 Mockito 单测拿不到 Spring 上下文，
     * 一加载类就炸——而"原文解析"恰恰是最该被单测钉住的一段（它决定证据能不能被采信）。
     */
    private final JsonMapper jsonMapper;

    @Override
    public Long record(AigSandboxRunRecordBo bo, Long operatorId) {
        AigReleaseTargetTypeEnum type = AigReleaseTargetTypeEnum.find(bo.getTargetType());
        if (type == null) {
            throw new ServiceException("未知的对象类型：" + bo.getTargetType()
                + "（只能是 AGENT_VERSION / SKILL_VERSION / PACKAGE_VERSION）");
        }
        assertTargetVersionExists(type, bo.getTargetVersionId());

        String raw = bo.getResultJson();
        JsonNode root;
        try {
            root = jsonMapper.readTree(raw);
        } catch (Exception e) {
            throw new ServiceException("result.json 不是合法 JSON，无法登记（请提交执行器原样输出，"
                + "不要手改）：" + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw new ServiceException("result.json 必须是一个 JSON 对象（执行器输出的一行结果）");
        }
        for (String field : REQUIRED_FIELDS) {
            JsonNode node = root.get(field);
            if (node == null || node.isNull() || (node.isString() && StringUtils.isBlank(node.stringValue()))) {
                throw new ServiceException("result.json 缺少必填字段 " + field
                    + "：这几个字段直接参与门槛判据，缺了就无法采信（不要手写 result.json，"
                    + "请提交执行器原样输出）");
            }
        }
        // timedOut 必须是真布尔值：它参与判据（超时=没跑通），字符串 "false" 不算
        if (!root.get("timedOut").isBoolean()) {
            throw new ServiceException("result.json 的 timedOut 不是布尔值：它参与门槛判据，"
                + "不接受字符串形式（不要手写 result.json，请提交执行器原样输出）");
        }
        if (!root.get("exitCode").isNumber()) {
            throw new ServiceException("result.json 的 exitCode 不是数字：它参与门槛判据"
                + "（不要手写 result.json，请提交执行器原样输出）");
        }

        String jobId = root.get("jobId").stringValue().trim();
        if (!jobId.equals(bo.getJobId().trim())) {
            throw new ServiceException("请求里的作业ID 与 result.json 里的 jobId 不一致（"
                + bo.getJobId().trim() + " != " + jobId
                + "）：把 A 作业的结果登记到 B 作业名下会让证据张冠李戴");
        }

        AigSandboxRun row = new AigSandboxRun();
        row.setTargetType(type.getCode());
        row.setTargetVersionId(bo.getTargetVersionId());
        row.setJobId(jobId);
        row.setAgentCode(StringUtils.isBlank(bo.getAgentCode()) ? null : bo.getAgentCode().trim());
        row.setImageRef(root.get("image").stringValue().trim());
        row.setExitCode(root.get("exitCode").asInt());
        row.setTimedOut(root.get("timedOut").booleanValue());
        row.setDurationMs(longOrNull(root, "durationMs"));
        row.setNetwork(root.get("network").stringValue().trim());
        row.setScratchFreeMb(longOrNull(root, "scratchFreeMb"));
        row.setArtifactCount(artifactCount(root));
        row.setResultSha256(sha256Hex(raw));
        row.setResultJson(raw);
        // 可信度来源**由服务端决定，不由提交方声明**：接口入参里根本没有这个字段。
        // 只有将来实现"执行器私钥签名 + 平台公钥验签"之后，验签通过才可能写 SIGNED；
        // 在那之前所有证据都是"人工登记、无密码学保证"，这一点必须如实入库。
        row.setAttestation(AigSandboxAttestationEnum.UNATTESTED.getCode());
        row.setRecordedBy(operatorId);
        row.setCreateTime(LocalDateTime.now());
        try {
            sandboxRunMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // job_id 唯一：同一作业只能登记一次。重复登记会把"跑通过几次"变成假账，
            // 而这正是这条路唯一要防的事——所以这里明确报错，不做"忽略重复"。
            throw new ServiceException("该作业已经登记过（jobId=" + jobId
                + "）：一个沙箱作业只能登记一次，重复登记会让证据变成假账。"
                + "若确需重新验证，请重新跑一次作业（新的 jobId）再登记");
        }
        log.info("登记沙箱运行证据, targetType={}, targetVersionId={}, jobId={}, image={}, "
                + "exitCode={}, timedOut={}, network={}, artifactCount={}, operatorId={}",
            row.getTargetType(), row.getTargetVersionId(), row.getJobId(), row.getImageRef(),
            row.getExitCode(), row.getTimedOut(), row.getNetwork(), row.getArtifactCount(), operatorId);
        return row.getSandboxRunId();
    }

    @Override
    public AigSandboxRunEvidence sandboxRunEvidence(String targetType, Long targetVersionId) {
        AigReleaseTargetTypeEnum type = AigReleaseTargetTypeEnum.find(targetType);
        if (type == null || targetVersionId == null) {
            return AigSandboxRunEvidence.unavailable("对象类型与版本ID不能为空");
        }
        // 只看最近一次：门槛问的是"这个版本现在能不能过"，
        // 允许"挑一次成功的算数"会让已经坏掉的版本拿着旧成绩单继续走（见证据类注释）
        AigSandboxRun latest = sandboxRunMapper.selectOne(new LambdaQueryWrapper<AigSandboxRun>()
            .eq(AigSandboxRun::getTargetType, type.getCode())
            .eq(AigSandboxRun::getTargetVersionId, targetVersionId)
            .orderByDesc(AigSandboxRun::getCreateTime)
            .orderByDesc(AigSandboxRun::getSandboxRunId)
            .last("limit 1"));
        return AigSandboxRunEvidence.evaluate(latest);
    }

    /**
     * 目标版本必须真实存在。
     *
     * <p>挡的是打错版本ID：错的ID在门槛处永远查不到证据（看起来像"没跑过"），
     * 而账本里却多了一条指向不存在版本的"证据"。这类脏数据的代价是排查时先怀疑错方向。</p>
     *
     * @param type 对象类型
     * @param id   版本ID
     */
    private void assertTargetVersionExists(AigReleaseTargetTypeEnum type, Long id) {
        boolean exists = switch (type) {
            case AGENT_VERSION -> agentVersionMapper.selectById(id) != null;
            case SKILL_VERSION -> skillVersionMapper.selectById(id) != null;
            case PACKAGE_VERSION -> packageVersionMapper.selectById(id) != null;
        };
        if (!exists) {
            throw new ServiceException(type.getDesc() + "不存在：" + id + "（版本ID 打错了吗？）");
        }
    }

    /**
     * 产物个数（{@code artifacts} 不是数组时按 0 计，不抛异常：它是摘要，不参与判据）。
     *
     * @param root 原文根节点
     * @return 产物个数
     */
    private int artifactCount(JsonNode root) {
        JsonNode artifacts = root.get("artifacts");
        return artifacts != null && artifacts.isArray() ? artifacts.size() : 0;
    }

    /**
     * 取可选的长整型字段。
     *
     * @param root  原文根节点
     * @param field 字段名
     * @return 值；缺失或非数字时返回 null
     */
    private Long longOrNull(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isNumber() ? node.asLong() : null;
    }

    /**
     * 对原文实算 SHA-256（留存以便事后核对"登记的就是执行器输出的"）。
     *
     * @param text 原文
     * @return 小写十六进制摘要
     */
    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然带 SHA-256；真缺了也不能静默留一个假哈希
            throw new ServiceException("计算 result.json 的 SHA-256 失败：" + e.getMessage());
        }
    }

}
