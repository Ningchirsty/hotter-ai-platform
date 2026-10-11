package org.dromara.ai.asset;

import org.dromara.asset.api.domain.MyAssetDTO;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

/**
 * 把图片/视频两域 {@code listOwnedAssets} 返回的行映射成跨域只读视图。
 *
 * <p>两个域的"我的素材"查询列名一致（{@code id / asset_type / source_kind / original_name /
 * content_type / size_bytes / task_id / create_time}），所以共用一份映射；
 * 这样"某一列取值方式"只有一处实现，不会出现图片域显示大小、视频域漏掉大小这类分叉。</p>
 *
 * <p>取值刻意**不猜**：拿不到就是 null（前端显示"—"），而不是把 0 或空串当成真实值——
 * "没有这个字段"与"值是 0"是两件事。</p>
 *
 * @author ai-gov
 */
public final class MyAssetRowMapper {

    private MyAssetRowMapper() {
    }

    /**
     * 行 → 视图。
     *
     * @param domain 域编码
     * @param row    JDBC 行（列名为下划线形式）
     * @return 视图（入参为空时返回 null）
     */
    public static MyAssetDTO toDto(String domain, Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        MyAssetDTO dto = new MyAssetDTO();
        dto.setDomain(domain);
        dto.setAssetId(longOf(row.get("id")));
        dto.setAssetType(stringOf(row.get("asset_type")));
        dto.setSourceKind(stringOf(row.get("source_kind")));
        dto.setName(stringOf(row.get("original_name")));
        dto.setMimeType(stringOf(row.get("content_type")));
        dto.setSizeBytes(longOf(row.get("size_bytes")));
        dto.setTaskId(longOf(row.get("task_id")));
        dto.setCreateTime(timeOf(row.get("create_time")));
        return dto;
    }

    /**
     * 取字符串。
     *
     * @param value 值
     * @return 字符串；空为 null
     */
    public static String stringOf(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 取长整型（不是数字且解析不出时返回 null）。
     *
     * @param value 值
     * @return 长整型；取不到为 null
     */
    public static Long longOf(Object value) {
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

    /**
     * 取时间。
     *
     * @param value 值
     * @return 时间；取不到为 null
     */
    public static LocalDateTime timeOf(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        }
        return null;
    }

}
