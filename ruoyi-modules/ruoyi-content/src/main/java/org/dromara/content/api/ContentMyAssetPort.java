package org.dromara.content.api;

import org.dromara.asset.api.MyAssetPort;
import org.dromara.asset.api.domain.MyAssetDTO;
import org.dromara.content.mapper.CpTaskFileMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 内容域的「我的附件」只读端口（{@link MyAssetPort} 的内容实现）。
 *
 * <h3>归属口径：看任务负责人，不看附件自己的创建人</h3>
 * <p>{@code cp_task_file} 上没有 {@code user_id}，只有 {@code task_id} 与 {@code create_by}。
 * 取 {@code create_by} 会把"由系统/协作方代为上传"的附件算到别人头上或漏掉；
 * 内容域自己的口径是 {@code cp_task.owner_id}（任务负责人），这里沿用同一条。</p>
 *
 * <p>内容域没有租户列，因此不做租户过滤（与内容域自身一致，不在这里凭空发明一个维度）。</p>
 *
 * @author ai-gov
 */
@Service
public class ContentMyAssetPort implements MyAssetPort {

    /**
     * 单次最多取多少条
     */
    private static final int MAX_LIMIT = 50;

    private final CpTaskFileMapper taskFileMapper;

    public ContentMyAssetPort(CpTaskFileMapper taskFileMapper) {
        this.taskFileMapper = taskFileMapper;
    }

    @Override
    public String domain() {
        return "CONTENT";
    }

    @Override
    public List<MyAssetDTO> listMyAssets(long userId, int limit) {
        if (userId <= 0) {
            return List.of();
        }
        int capped = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<Map<String, Object>> rows = taskFileMapper.listOwnedAssetRows(userId, capped);
        List<MyAssetDTO> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            MyAssetDTO dto = new MyAssetDTO();
            dto.setDomain(domain());
            dto.setAssetId(longOf(row.get("file_id")));
            dto.setAssetType(stringOf(row.get("file_kind")));
            dto.setSourceKind(null);
            dto.setName(stringOf(row.get("file_name")));
            // 内容域只存文件引用，没有 MIME 列：留着 null，不猜
            dto.setMimeType(null);
            dto.setSizeBytes(longOf(row.get("file_size")));
            dto.setTaskId(longOf(row.get("task_id")));
            dto.setCreateTime(timeOf(row.get("create_time")));
            result.add(dto);
        }
        return result;
    }

    private static String stringOf(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Long longOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime timeOf(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
        }
        return null;
    }

}
