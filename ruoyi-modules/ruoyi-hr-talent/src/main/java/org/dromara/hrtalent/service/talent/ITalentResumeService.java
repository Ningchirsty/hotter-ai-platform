package org.dromara.hrtalent.service.talent;

import jakarta.servlet.http.HttpServletResponse;
import org.dromara.hrtalent.domain.bo.talent.ResumeDownloadBo;
import org.dromara.hrtalent.domain.bo.talent.TalentResumeQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentResumeUploadBo;
import org.dromara.hrtalent.domain.entity.TalentResume;
import org.dromara.hrtalent.domain.vo.talent.TalentResumeVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 人才简历版本服务接口（SPEC-P4 §2.1，设计文档 §8.13、§8.8、§11.1）。
 *
 * <p><b>硬约束</b>：</p>
 * <ul>
 *     <li>上传<b>只新增版本，绝不覆盖旧文件</b>；{@code version_no} 单调递增，
 *     同一人才仅一条 {@code current_flag = '1'}；</li>
 *     <li>简历一律走 {@code hr_talent_resume}，<b>不</b>写入通用附件表 {@code hr_recruit_attachment}（§8.8）；</li>
 *     <li>所有读写入口<b>先</b>经人才资源级鉴权（{@code TalentScopeDomainService}，经人才主档服务收敛），
 *     不可见时一律拒绝，判定规则不在本服务内另写（§11.1）；</li>
 *     <li>下载<b>不</b>返回对象存储长期地址，只做鉴权后流式输出（§8.8、§11.1）。</li>
 * </ul>
 *
 * @author hr-talent
 */
public interface ITalentResumeService {

    /**
     * 查询指定人才的简历版本列表（自动完成人才资源级鉴权）。
     *
     * @param talentId 人才主档ID
     * @param bo       可选过滤条件
     * @return 简历版本列表（按版本号倒序）
     */
    List<TalentResumeVo> listByTalent(Long talentId, TalentResumeQueryBo bo);

    /**
     * 上传简历新版本（不覆盖旧文件；新版本自动成为当前版本）。
     *
     * @param talentId 人才主档ID
     * @param bo       上传业务对象，可为空
     * @param file     上传文件
     * @return 新建的简历版本视图对象（含重复文件提示）
     */
    TalentResumeVo upload(Long talentId, TalentResumeUploadBo bo, MultipartFile file);

    /**
     * 指定某简历版本为当前简历（事务内切换，保证同一人才仅一条当前版本）。
     *
     * @param resumeId 简历版本ID
     */
    void setCurrent(Long resumeId);

    /**
     * 查询某简历版本所属人才的全部版本列表（自动完成人才资源级鉴权）。
     *
     * @param resumeId 简历版本ID
     * @return 简历版本列表（按版本号倒序）
     */
    List<TalentResumeVo> versions(Long resumeId);

    /**
     * 受控下载简历：鉴权（可见范围 + 授权级别）→ 写审计 → 流式返回，全程不产生签名地址。
     *
     * <p><b>鉴权口径</b>（设计文档 §8.19 + §6）：可见范围只回答「能不能看到这条人才」；
     * 若该人才的可见性为<b>显式授权</b>（{@code explicit}），下载还必须达到共享授权级别
     * {@code attachment}（附件级）——只被授予 summary（脱敏摘要）/ detail 的用户即便持有
     * {@code talent:resume:download} 按钮权限也不得下载；通过集团共享、归属公司、归属部门、
     * 人才负责人等<b>职责范围内</b>方式可见的按原口径放行；超管直接放行。</p>
     *
     * @param resumeId 简历版本ID
     * @param bo       下载业务对象（用途必填）
     * @param response HTTP 响应
     */
    void download(Long resumeId, ResumeDownloadBo bo, HttpServletResponse response);

    /**
     * 按主键读取简历并完成人才资源级鉴权（供解析域复用，避免两处各写一套授权规则）。
     *
     * @param resumeId 简历版本ID
     * @return 可见的简历实体
     */
    TalentResume requireVisibleResume(Long resumeId);

}
