package org.dromara.aigov.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.knowledge.domain.AigKnowledgeCollection;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocument;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocumentVersion;
import org.dromara.aigov.knowledge.domain.AigKnowledgeProcessingJob;
import org.dromara.aigov.knowledge.domain.bo.AigKnowledgeCollectionBo;
import org.dromara.aigov.knowledge.domain.bo.AigKnowledgeDocumentBo;
import org.dromara.aigov.knowledge.domain.vo.AigKnowledgeDocumentRegisterVo;
import org.dromara.aigov.knowledge.enums.AigKnowledgeJobStatusEnum;
import org.dromara.aigov.knowledge.enums.AigKnowledgeSourceTypeEnum;
import org.dromara.aigov.knowledge.enums.AigKnowledgeStatusEnum;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeCollectionMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeDocumentMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeDocumentVersionMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeProcessingJobMapper;
import org.dromara.aigov.knowledge.service.IAigKnowledgeService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 知识中心服务实现（设计 §6.4、§8.2）。
 *
 * <p>实现纪律：所有状态变更都带"当前状态"条件更新（CAS），并发下宁可报冲突也不静默覆盖。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigKnowledgeServiceImpl implements IAigKnowledgeService {

    private static final String STATUS_NORMAL = "0";

    private final AigKnowledgeCollectionMapper collectionMapper;
    private final AigKnowledgeDocumentMapper documentMapper;
    private final AigKnowledgeDocumentVersionMapper versionMapper;
    private final AigKnowledgeProcessingJobMapper jobMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCollection(AigKnowledgeCollectionBo bo, Long actorId) {
        if (bo == null || StringUtils.isBlank(bo.getCollectionCode())) {
            throw new ServiceException("集合编码不能为空");
        }
        String code = bo.getCollectionCode().trim();
        Long exists = collectionMapper.selectCount(new LambdaQueryWrapper<AigKnowledgeCollection>()
            .eq(AigKnowledgeCollection::getCollectionCode, code));
        if (exists != null && exists > 0L) {
            throw new ServiceException("集合编码已存在：" + code + "（集合编码是授权与统计的口径，必须唯一）");
        }
        String dataLevel = StringUtils.blankToDefault(bo.getDataLevel(), AigDataLevelEnum.STRICT.getCode());
        if (AigDataLevelEnum.find(dataLevel) == null) {
            throw new ServiceException("非法的数据等级：" + bo.getDataLevel());
        }
        AigKnowledgeCollection collection = new AigKnowledgeCollection();
        collection.setCollectionCode(code);
        collection.setCollectionName(StringUtils.blankToDefault(bo.getCollectionName(), code));
        collection.setOwnerId(actorId);
        collection.setOrgScope(bo.getOrgScope());
        // 默认严格级：知识资料通常是企业禁区，默认放低反而危险（设计 §4.1）
        collection.setDataLevel(dataLevel);
        collection.setStatus(STATUS_NORMAL);
        collection.setDelFlag(STATUS_NORMAL);
        collection.setRemark(bo.getRemark());
        collectionMapper.insert(collection);
        log.info("新建知识集合, collectionId={}, code={}, dataLevel={}",
            collection.getCollectionId(), code, dataLevel);
        return collection.getCollectionId();
    }

    @Override
    public List<AigKnowledgeCollection> listCollections() {
        return collectionMapper.selectList(new LambdaQueryWrapper<AigKnowledgeCollection>()
            .eq(AigKnowledgeCollection::getStatus, STATUS_NORMAL)
            .orderByAsc(AigKnowledgeCollection::getCollectionCode));
    }

    @Override
    public List<AigKnowledgeDocument> listDocuments(Long collectionId) {
        return documentMapper.selectList(new LambdaQueryWrapper<AigKnowledgeDocument>()
            .eq(AigKnowledgeDocument::getStatus, STATUS_NORMAL)
            .eq(collectionId != null, AigKnowledgeDocument::getCollectionId, collectionId)
            .orderByDesc(AigKnowledgeDocument::getDocumentId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigKnowledgeDocumentRegisterVo registerDocument(AigKnowledgeDocumentBo bo, Long actorId) {
        if (bo == null || bo.getCollectionId() == null) {
            throw new ServiceException("知识集合不能为空");
        }
        AigKnowledgeCollection collection = collectionMapper.selectById(bo.getCollectionId());
        if (collection == null || !STATUS_NORMAL.equals(collection.getStatus())) {
            throw new ServiceException("知识集合不存在或已停用：" + bo.getCollectionId());
        }
        AigKnowledgeSourceTypeEnum sourceType = AigKnowledgeSourceTypeEnum.fromFileName(bo.getSourceName());
        if (sourceType == null) {
            throw new ServiceException("不支持的知识文件格式（首期支持 PDF/DOCX/TXT/Markdown）："
                + bo.getSourceName() + "。XLSX 请走结构化业务接口导入（设计 §6.4）");
        }
        // 重复文件提示：同哈希的既有版本。**只提示不拒绝**——不同业务范围不能共享授权
        AigKnowledgeDocumentVersion duplicate = versionMapper.selectOne(
            new LambdaQueryWrapper<AigKnowledgeDocumentVersion>()
                .eq(AigKnowledgeDocumentVersion::getSha256, bo.getSha256().trim())
                .last("limit 1"));

        AigKnowledgeDocument document = new AigKnowledgeDocument();
        document.setCollectionId(collection.getCollectionId());
        document.setSourceName(bo.getSourceName().trim());
        document.setSourceType(sourceType.getCode());
        document.setOwnerId(actorId);
        document.setStatus(STATUS_NORMAL);
        document.setDelFlag(STATUS_NORMAL);
        document.setRemark(bo.getRemark());
        documentMapper.insert(document);
        if (document.getDocumentId() == null) {
            throw new ServiceException("登记文档失败：未取回主键");
        }

        String version = StringUtils.blankToDefault(bo.getVersion(), "1");
        Long sameVersion = versionMapper.selectCount(new LambdaQueryWrapper<AigKnowledgeDocumentVersion>()
            .eq(AigKnowledgeDocumentVersion::getDocumentId, document.getDocumentId())
            .eq(AigKnowledgeDocumentVersion::getVersion, version));
        if (sameVersion != null && sameVersion > 0L) {
            throw new ServiceException("该文档已存在同版本号：" + version + "（版本号是原文不可变的锚点，不能复用）");
        }

        AigKnowledgeDocumentVersion entity = new AigKnowledgeDocumentVersion();
        entity.setDocumentId(document.getDocumentId());
        entity.setVersion(version);
        entity.setSha256(bo.getSha256().trim());
        entity.setSourceRef(bo.getSourceRef());
        entity.setEffectiveFrom(bo.getEffectiveFrom());
        entity.setEffectiveTo(bo.getEffectiveTo());
        entity.setStatus(AigKnowledgeStatusEnum.UPLOADED.getCode());
        entity.setDelFlag(STATUS_NORMAL);
        entity.setRemark(bo.getRemark());
        versionMapper.insert(entity);

        AigKnowledgeDocumentRegisterVo vo = new AigKnowledgeDocumentRegisterVo();
        vo.setDocumentId(document.getDocumentId());
        vo.setVersionId(entity.getVersionId());
        vo.setVersion(version);
        vo.setStatus(entity.getStatus());
        vo.setDuplicateHint(duplicate != null);
        vo.setDuplicateVersionId(duplicate == null ? null : duplicate.getVersionId());
        log.info("登记知识文档版本, documentId={}, versionId={}, version={}, duplicateHint={}",
            document.getDocumentId(), entity.getVersionId(), version, vo.getDuplicateHint());
        return vo;
    }

    @Override
    public AigKnowledgeDocumentVersion getVersion(Long versionId) {
        if (versionId == null) {
            throw new ServiceException("版本ID不能为空");
        }
        AigKnowledgeDocumentVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new ServiceException("文档版本不存在或已删除：" + versionId);
        }
        return version;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigKnowledgeStatusEnum advanceProcessing(Long versionId, String idempotencyKey) {
        AigKnowledgeDocumentVersion version = getVersion(versionId);
        if (StringUtils.isBlank(idempotencyKey)) {
            throw new ServiceException("幂等键不能为空（重试必须复用同一个键，否则会重复索引）");
        }
        AigKnowledgeStatusEnum current = AigKnowledgeStatusEnum.find(version.getStatus());
        if (current == null) {
            throw new ServiceException("文档版本状态不可识别：" + version.getStatus());
        }
        if (current == AigKnowledgeStatusEnum.RETIRED) {
            throw new ServiceException("版本已退役，不能再推进处理：" + versionId);
        }
        // 幂等：同一键已经推进过一次，就不再推进（重试落到同一条任务）
        if (alreadyProcessed(versionId, idempotencyKey)) {
            log.info("处理阶段已按该幂等键推进过，返回当前状态：versionId={} key={}", versionId, idempotencyKey);
            return current;
        }
        AigKnowledgeStatusEnum next = current.next();
        if (current == AigKnowledgeStatusEnum.FAILED) {
            // 失败后重试：从扫描阶段重新开始（不跳过安全检查）
            next = AigKnowledgeStatusEnum.SCANNING;
        }
        if (next == null) {
            // 已是 READY：幂等返回，不报错
            return current;
        }
        recordJob(versionId, next, AigKnowledgeJobStatusEnum.SUCCEEDED, idempotencyKey, null, null);
        casStatus(version, current, next);
        log.info("知识处理推进, versionId={}, {} -> {}", versionId, current.getCode(), next.getCode());
        return next;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigKnowledgeStatusEnum failProcessing(Long versionId, String idempotencyKey,
                                                 String errorCode, String errorMessage) {
        AigKnowledgeDocumentVersion version = getVersion(versionId);
        AigKnowledgeStatusEnum current = AigKnowledgeStatusEnum.find(version.getStatus());
        if (current == null) {
            throw new ServiceException("文档版本状态不可识别：" + version.getStatus());
        }
        if (current.isTerminal() && current != AigKnowledgeStatusEnum.FAILED) {
            // READY/RETIRED 是终态：不允许被一次失败改写（否则会丢掉"它曾经可用"的事实）
            return current;
        }
        String key = StringUtils.blankToDefault(idempotencyKey, "fail-" + versionId);
        if (!alreadyProcessed(versionId, key)) {
            recordJob(versionId, current, AigKnowledgeJobStatusEnum.FAILED, key, errorCode, errorMessage);
        }
        casStatus(version, current, AigKnowledgeStatusEnum.FAILED);
        log.warn("知识处理失败, versionId={}, stage={}, errorCode={}, reason={}",
            versionId, current.getCode(), errorCode, errorMessage);
        return AigKnowledgeStatusEnum.FAILED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigKnowledgeStatusEnum retire(Long versionId) {
        AigKnowledgeDocumentVersion version = getVersion(versionId);
        AigKnowledgeStatusEnum current = AigKnowledgeStatusEnum.find(version.getStatus());
        if (current == null) {
            throw new ServiceException("文档版本状态不可识别：" + version.getStatus());
        }
        if (current == AigKnowledgeStatusEnum.RETIRED) {
            return AigKnowledgeStatusEnum.RETIRED;
        }
        casStatus(version, current, AigKnowledgeStatusEnum.RETIRED);
        log.info("知识版本退役, versionId={}, from={}", versionId, current.getCode());
        return AigKnowledgeStatusEnum.RETIRED;
    }

    /**
     * 该幂等键是否已经写过一条处理任务（重试去重）。
     *
     * @param versionId      版本ID
     * @param idempotencyKey 幂等键
     * @return 已存在返回 true
     */
    private boolean alreadyProcessed(Long versionId, String idempotencyKey) {
        Long existing = jobMapper.selectCount(new LambdaQueryWrapper<AigKnowledgeProcessingJob>()
            .eq(AigKnowledgeProcessingJob::getDocumentVersionId, versionId)
            .eq(AigKnowledgeProcessingJob::getIdempotencyKey, idempotencyKey));
        return existing != null && existing > 0L;
    }

    /**
     * 写一条处理任务。
     */
    private void recordJob(Long versionId, AigKnowledgeStatusEnum stage, AigKnowledgeJobStatusEnum status,
                           String idempotencyKey, String errorCode, String errorMessage) {
        AigKnowledgeProcessingJob job = new AigKnowledgeProcessingJob();
        job.setDocumentVersionId(versionId);
        job.setStage(stage.getCode());
        job.setStatus(status.getCode());
        job.setAttempt(1);
        job.setErrorCode(errorCode);
        job.setErrorMessage(StringUtils.substring(errorMessage, 0, 500));
        job.setIdempotencyKey(idempotencyKey);
        job.setDelFlag(STATUS_NORMAL);
        jobMapper.insert(job);
    }

    /**
     * 带当前状态条件地更新版本状态（CAS）：影响 0 行说明状态被并发改过，报冲突而不是覆盖。
     */
    private void casStatus(AigKnowledgeDocumentVersion version, AigKnowledgeStatusEnum from,
                           AigKnowledgeStatusEnum to) {
        AigKnowledgeDocumentVersion update = new AigKnowledgeDocumentVersion();
        update.setVersionId(version.getVersionId());
        update.setStatus(to.getCode());
        int rows = versionMapper.update(update, Wrappers.<AigKnowledgeDocumentVersion>lambdaUpdate()
            .eq(AigKnowledgeDocumentVersion::getVersionId, version.getVersionId())
            .eq(AigKnowledgeDocumentVersion::getStatus, from.getCode()));
        if (rows == 0) {
            throw new ServiceException("文档版本状态已被其它操作改变，请刷新后重试：versionId="
                + version.getVersionId());
        }
    }

}
