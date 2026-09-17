package org.dromara.talent.service;

import jakarta.servlet.http.HttpServletResponse;
import org.dromara.talent.domain.vo.TlTalentAttachmentVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 人才附件服务（简历 / 证件），含版本管理与扫描状态机。
 *
 * @author talent
 */
public interface ITalentAttachmentService {

    /**
     * 某人才的全部附件（当前版本在前）。
     *
     * @param talentId 人才ID
     * @return 附件列表
     */
    List<TlTalentAttachmentVo> listByTalent(Long talentId);

    /**
     * 上传新版本；返回 attachmentId。执行扩展名 / 大小 / MIME / 权限校验并写审计。
     *
     * @param talentId       人才ID
     * @param attachmentType 附件类型
     * @param file           文件
     * @return 附件ID
     */
    Long upload(Long talentId, String attachmentType, MultipartFile file);

    /**
     * 受控流式下载：二次校验单条授权与扫描状态，写审计。
     *
     * @param attachmentId 附件ID
     * @param response     HTTP 响应
     */
    void download(Long attachmentId, HttpServletResponse response);

    /**
     * 逻辑删除附件（保留对象）；写审计。
     *
     * @param attachmentId 附件ID
     */
    void remove(Long attachmentId);

    /**
     * 扫描回调：更新 scan_status / scan_time / scan_remark。
     *
     * @param attachmentId 附件ID
     * @param scanStatus   扫描状态
     * @param remark       扫描说明
     */
    void updateScanStatus(Long attachmentId, String scanStatus, String remark);

}
