package org.dromara.aigov.agent.helper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.domain.AigPackageRejection;
import org.dromara.aigov.agent.mapper.AigPackageRejectionMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Package 注册被拒的证据登记（<b>独立事务</b>）。
 *
 * <p><b>为什么必须单独一个 Bean、且 REQUIRES_NEW</b>：与制品的被拒登记（{@code AigArtifactRecorder}
 * 一路）同因——注册失败时调用方会拿到异常，而它正处在注册的事务里；把证据写在同一个事务，
 * 异常一抛证据就跟着回滚，库里只剩 oper_log 一条异常。于是一句"留痕"只在成功路径上成立，
 * 而那正是最不需要它的路径。</p>
 *
 * <p><b>写入前一律截断到列宽</b>：被拒往往正是因为字段畸形（超长、非 JSON、二进制垃圾），
 * 不截断会让"记录拒绝原因"这句 SQL 自己报 {@code Data too long}，
 * 把干净的业务拒绝变成 500，而证据仍然没落库。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AigPackageRejectionRecorder {

    /**
     * 拒绝原因：Manifest 不合法
     */
    public static final String REASON_MANIFEST_INVALID = "MANIFEST_INVALID";

    /**
     * 拒绝原因：包体校验和不匹配
     */
    public static final String REASON_CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH";

    /**
     * 拒绝原因：包体内容安全检查不通过
     */
    public static final String REASON_ARCHIVE_UNSAFE = "ARCHIVE_UNSAFE";

    private static final int CODE_MAX = 64;
    private static final int VERSION_MAX = 32;
    private static final int BODY_NAME_MAX = 255;
    private static final int RULES_MAX = 500;
    private static final int DETAIL_MAX = 1000;

    private final AigPackageRejectionMapper rejectionMapper;

    /**
     * 落一条被拒证据。
     *
     * @param packageCode    包编码（可能为空：Manifest 都没解析出来时）
     * @param packageVersion 包版本（可能为空）
     * @param bodyName       上传文件名（可能为空）
     * @param body           包体（可能为空：连体都没拿到）
     * @param bodySha256     包体 SHA-256（可能为空）
     * @param rejectReason   拒绝原因（见本类常量）
     * @param hitRules       命中规则码（逗号分隔，可空）
     * @param detail         可读明细
     * @param operatorId     操作者ID（可空=系统触发）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(String packageCode, String packageVersion, String bodyName, byte[] body,
                       String bodySha256, String rejectReason, String hitRules, String detail,
                       Long operatorId) {
        AigPackageRejection row = new AigPackageRejection();
        row.setPackageCode(truncate(packageCode, CODE_MAX));
        row.setPackageVersion(truncate(packageVersion, VERSION_MAX));
        row.setBodyName(truncate(bodyName, BODY_NAME_MAX));
        row.setBodySha256(truncate(bodySha256, 64));
        row.setBodySize(body == null ? null : (long) body.length);
        row.setRejectReason(truncate(rejectReason, 32));
        row.setHitRules(truncate(hitRules, RULES_MAX));
        row.setDetail(truncate(detail, DETAIL_MAX));
        row.setOperatorId(operatorId);
        row.setCreateTime(LocalDateTime.now());
        rejectionMapper.insert(row);
        log.warn("Package 注册被拒并留痕: packageCode={}, version={}, reason={}, 规则={}, 明细={}",
            row.getPackageCode(), row.getPackageVersion(), row.getRejectReason(), row.getHitRules(),
            row.getDetail());
    }

    /**
     * 截断到列宽（null 安全）。
     *
     * @param value 原值
     * @param max   列宽
     * @return 截断后的值；null 原样返回（这些列可空，空比空串更诚实）
     */
    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

}
