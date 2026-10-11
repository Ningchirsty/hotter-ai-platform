package org.dromara.aigov.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.config.AigSandboxArtifactProperties;
import org.dromara.aigov.agent.domain.AigSandboxArtifact;
import org.dromara.aigov.agent.domain.AigSandboxRun;
import org.dromara.aigov.agent.domain.vo.AigSandboxArtifactManifestVo;
import org.dromara.aigov.agent.helper.AigSandboxArtifactManifest;
import org.dromara.aigov.agent.helper.IAigSandboxArtifactStore;
import org.dromara.aigov.agent.mapper.AigSandboxArtifactMapper;
import org.dromara.aigov.agent.mapper.AigSandboxRunMapper;
import org.dromara.aigov.agent.service.IAigSandboxArtifactService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 沙箱产物服务实现（ADR-016）。
 *
 * <h3>三道校验，缺一不可</h3>
 * <ol>
 *     <li><b>路径在声明清单内</b>：清单来自已存证的 {@code result.json}，上传方塞不进新文件；</li>
 *     <li><b>字节数一致</b>：与声明比对；</li>
 *     <li><b>SHA-256 一致</b>：<b>服务端对上传字节实算</b>再与声明比对——不采信上传方报的摘要。</li>
 * </ol>
 * <p>三条都过才写对象存储，且对象键由 {@link IAigSandboxArtifactStore#keyOf} 按摘要生成。
 * 任何一条不过都<b>不进对象存储</b>（先校验后落盘）：否则对象存储里会留下
 * 一份"账本上没有、也不知道属于谁"的字节。</p>
 *
 * <h3>下载为什么还要再实算一次摘要</h3>
 * <p>对象键本身就是摘要，理论上取回来的就该是那些字节。但对象存储可能被误操作（桶混了、
 * 对象被覆盖），而<b>把与证据不符的字节交给人</b>比报错严重得多——所以取回来再算一次，
 * 不一致就拒绝下发。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigSandboxArtifactServiceImpl implements IAigSandboxArtifactService {

    private final AigSandboxArtifactMapper artifactMapper;

    private final AigSandboxRunMapper sandboxRunMapper;

    private final IAigSandboxArtifactStore artifactStore;

    private final AigSandboxArtifactProperties properties;

    /**
     * 注入 {@link JsonMapper} 而不是 {@code JsonUtils}：后者静态初始化时走 Spring 容器，
     * 纯 Mockito 单测一加载就炸——而"原文解析"恰恰是最该被单测钉住的一段。
     */
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upload(Long sandboxRunId, String path, byte[] body, String declaredContentType,
                       Long actorId) {
        AigSandboxRun run = requireRun(sandboxRunId);
        if (body == null) {
            throw new ServiceException("产物字节不能为空：请选择要登记的文件");
        }
        Map<String, AigSandboxArtifactManifest.Entry> manifest = parseManifest(run);
        String normalized = normalize(path);
        AigSandboxArtifactManifest.Entry declared = manifest.get(normalized);
        if (declared == null) {
            throw new ServiceException("result.json 里没有声明这个产物：" + normalized
                + "。只能登记账本声明过的产物（声明清单："
                + (manifest.isEmpty() ? "空" : String.join("、", manifest.keySet())) + "）");
        }

        assertWithinLimits(sandboxRunId, body.length);

        if (body.length != declared.bytes()) {
            throw new ServiceException("字节数与 result.json 声明不一致：声明 " + declared.bytes()
                + " 字节，实际 " + body.length + " 字节（路径 " + normalized + "）");
        }
        String actualSha = AigSandboxArtifactManifest.sha256(body);
        if (!actualSha.equalsIgnoreCase(declared.sha256())) {
            throw new ServiceException("SHA-256 与 result.json 声明不一致（路径 " + normalized + "）：声明 "
                + declared.sha256() + "，服务端实算 " + actualSha
                + "。字节内容与账本不符，拒绝入库");
        }

        AigSandboxArtifact existing = findByPath(sandboxRunId, normalized);
        if (existing != null) {
            throw new ServiceException("这个产物已经登记过（路径 " + normalized + "，产物ID "
                + existing.getArtifactId() + "）：同一产物只能登记一次，重复登记会让「存了几份」变成假账");
        }

        AigSandboxArtifact row = new AigSandboxArtifact();
        row.setSandboxRunId(sandboxRunId);
        row.setPath(normalized);
        row.setBytes((long) body.length);
        row.setSha256(actualSha);
        row.setObjectKey(artifactStore.keyOf(sandboxRunId, actualSha));
        // 上传方声明的类型：只记录、截断；下载时绝不回显（见 controller 的三条硬要求）
        String declaredType = declaredContentType == null ? null : declaredContentType.trim();
        row.setContentType(StringUtils.isBlank(declaredType) ? null
            : StringUtils.substring(declaredType, 0, 128));
        row.setUploadedBy(actorId);
        row.setCreateTime(LocalDateTime.now());

        // 先落对象存储再写账本：反过来会得到"账本说有、取的时候没有"
        artifactStore.put(row.getObjectKey(), body);
        try {
            artifactMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // (sandbox_run_id, path) 唯一：并发下另一方先插进去了。对象键按摘要生成，
            // 两边写的是同一份字节，所以这里只需如实报"已登记过"
            throw new ServiceException("这个产物已经被并发登记（路径 " + normalized
                + "）：同一产物只能登记一次");
        }
        log.info("登记沙箱产物, sandboxRunId={}, path={}, bytes={}, uploadedBy={}",
            sandboxRunId, normalized, body.length, actorId);
        return row.getArtifactId();
    }

    @Override
    public List<AigSandboxArtifactManifestVo> list(Long sandboxRunId) {
        AigSandboxRun run = requireRun(sandboxRunId);
        Map<String, AigSandboxArtifactManifest.Entry> manifest = parseManifest(run);
        Map<String, AigSandboxArtifact> stored = new LinkedHashMap<>();
        for (AigSandboxArtifact row : artifactMapper.selectList(
            new LambdaQueryWrapper<AigSandboxArtifact>()
                .eq(AigSandboxArtifact::getSandboxRunId, sandboxRunId))) {
            stored.put(row.getPath(), row);
        }

        List<AigSandboxArtifactManifestVo> result = new ArrayList<>(manifest.size());
        for (AigSandboxArtifactManifest.Entry entry : manifest.values()) {
            AigSandboxArtifact row = stored.get(entry.path());
            AigSandboxArtifactManifestVo vo = new AigSandboxArtifactManifestVo();
            vo.setPath(entry.path());
            vo.setBytes(entry.bytes());
            vo.setSha256(entry.sha256());
            vo.setUploaded(row != null);
            if (row != null) {
                vo.setArtifactId(row.getArtifactId());
                vo.setContentType(row.getContentType());
                vo.setUploadedAt(row.getCreateTime());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public AigSandboxArtifactFile download(Long sandboxRunId, String path) {
        AigSandboxRun run = requireRun(sandboxRunId);
        String normalized = normalize(path);
        AigSandboxArtifact row = findByPath(sandboxRunId, normalized);
        if (row == null) {
            throw new ServiceException("这个产物还没登记字节（路径 " + normalized
                + "）：请先上传，再下载（账本里只有声明，字节要单独交上来）");
        }
        // 账本自洽检查：声明与入库记录必须是同一份摘要，否则先别下发
        AigSandboxArtifactManifest.Entry declared = parseManifest(run).get(normalized);
        if (declared == null || !declared.sha256().equalsIgnoreCase(row.getSha256())) {
            throw new ServiceException("账本声明与入库记录的摘要不一致（路径 " + normalized
                + "）：拒绝下发，请核对 aig_sandbox_artifact 与 result.json");
        }

        byte[] content = artifactStore.get(row.getObjectKey());
        String actualSha = AigSandboxArtifactManifest.sha256(content);
        if (!actualSha.equalsIgnoreCase(row.getSha256())) {
            // 对象存储被误操作（桶混了/对象被覆盖）：宁可报错，也不把与证据不符的字节交给人
            throw new ServiceException("取回的字节与账本摘要不一致（路径 " + normalized + "）：账本 "
                + row.getSha256() + "，实读 " + actualSha + "。拒绝下发，请检查对象存储");
        }
        log.info("下发沙箱产物, sandboxRunId={}, path={}, bytes={}, operatorId={}",
            sandboxRunId, normalized, content.length, row.getUploadedBy());
        return new AigSandboxArtifactFile(content,
            AigSandboxArtifactManifest.downloadFileName(normalized), row.getSha256());
    }

    /* ------------------------------------------------------------------ 内部 */

    /**
     * 读沙箱运行记录（不存在即拒：产物必须挂在一次真实登记过的作业上）。
     *
     * @param sandboxRunId 运行记录ID
     * @return 运行记录
     */
    private AigSandboxRun requireRun(Long sandboxRunId) {
        if (sandboxRunId == null) {
            throw new ServiceException("沙箱运行记录ID不能为空");
        }
        AigSandboxRun run = sandboxRunMapper.selectById(sandboxRunId);
        if (run == null) {
            throw new ServiceException("沙箱运行记录不存在：" + sandboxRunId
                + "（产物必须挂在一次已登记的作业上）");
        }
        return run;
    }

    /**
     * 解析账本声明（解析失败统一转成业务异常，带上可执行的原因）。
     *
     * @param run 运行记录
     * @return 归一化路径 → 声明
     */
    private Map<String, AigSandboxArtifactManifest.Entry> parseManifest(AigSandboxRun run) {
        try {
            return AigSandboxArtifactManifest.parse(run.getResultJson(), jsonMapper);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("无法校验产物清单：" + e.getMessage());
        }
    }

    /**
     * 归一化路径（失败转业务异常）。
     *
     * @param path 原始路径
     * @return 归一化路径
     */
    private String normalize(String path) {
        try {
            return AigSandboxArtifactManifest.normalizePath(path);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    /**
     * 限额校验（单文件 / 每作业文件数 / 每作业总量）。
     *
     * @param runIdForLimits 运行记录ID
     * @param incomingBytes  本次字节数
     */
    private void assertWithinLimits(Long runIdForLimits, long incomingBytes) {
        if (incomingBytes > properties.getMaxFileBytes()) {
            throw new ServiceException("单个产物超过上限：" + incomingBytes + " > "
                + properties.getMaxFileBytes() + " 字节（aigov.sandbox.artifact.max-file-bytes）。"
                + "若要登记更大的产物，请先与服务端一起评估下载侧的内存占用");
        }
        List<AigSandboxArtifact> rows = artifactMapper.selectList(
            new LambdaQueryWrapper<AigSandboxArtifact>()
                .eq(AigSandboxArtifact::getSandboxRunId, runIdForLimits));
        if (rows.size() + 1 > properties.getMaxFilesPerRun()) {
            throw new ServiceException("这个作业的产物数量已达上限（" + properties.getMaxFilesPerRun()
                + " 个）：不再接受新上传（aigov.sandbox.artifact.max-files-per-run）");
        }
        long total = incomingBytes;
        for (AigSandboxArtifact row : rows) {
            total += row.getBytes() == null ? 0L : row.getBytes();
        }
        if (total > properties.getMaxTotalBytesPerRun()) {
            throw new ServiceException("这个作业的产物总字节数将超过上限（"
                + properties.getMaxTotalBytesPerRun() + " 字节）：不再接受新上传"
                + "（aigov.sandbox.artifact.max-total-bytes-per-run）");
        }
    }

    /**
     * 查已入库的产物行。
     *
     * @param sandboxRunId 运行记录ID
     * @param path         归一化路径
     * @return 行；不存在返回 null
     */
    private AigSandboxArtifact findByPath(Long sandboxRunId, String path) {
        return artifactMapper.selectOne(new LambdaQueryWrapper<AigSandboxArtifact>()
            .eq(AigSandboxArtifact::getSandboxRunId, sandboxRunId)
            .eq(AigSandboxArtifact::getPath, path)
            .last("limit 1"));
    }

}
