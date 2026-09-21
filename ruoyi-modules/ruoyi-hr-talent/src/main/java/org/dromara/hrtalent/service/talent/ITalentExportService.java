package org.dromara.hrtalent.service.talent;

import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.talent.TalentExportCreateBo;
import org.dromara.hrtalent.domain.bo.talent.TalentExportQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentExportTaskVo;

/**
 * 人才导出任务服务接口（SPEC-P4 §2.6 F 线、设计文档 §8.20）。
 *
 * <p><b>接口契约</b>：</p>
 * <ul>
 *     <li>{@code POST /talent/profiles/export} → {@link #createExport}（权限 {@code talent:profile:export}）；</li>
 *     <li>{@code GET /talent/exports} → {@link #queryPage}；</li>
 *     <li>{@code GET /talent/exports/{id}/download} → {@link #download}（<b>用途必填</b>）。</li>
 * </ul>
 *
 * <p><b>授权硬约束</b>：</p>
 * <ul>
 *     <li>导出结果的可见范围统一由 {@code TalentScopeDomainService} 叠加，
 *     本服务实现<b>不</b>自写授权规则（设计文档 §8.17、§11.1）；</li>
 *     <li>敏感台账在按钮权限之外，<b>额外</b>要求独立权限
 *     （{@code HrTalentConstants.PERM_PROFILE_PHONE_VIEW}）与非空用途 {@code purpose}；</li>
 *     <li>结果文件只存私有对象存储的对象标识，对外只提供系统内受控下载地址（§8.13、§11.1）。</li>
 * </ul>
 *
 * @author hr-talent
 */
public interface ITalentExportService {

    /**
     * 创建人才导出（本阶段为同步生成结果文件 + 单次最大条数上限）。
     *
     * <p><b>规模控制</b>：超过 {@code hrtalent.export-max-rows}（缺省 10000）时<b>直接拒绝</b>并提示缩小范围，
     * 不静默截断——静默截断会让使用者误以为「导出的就是全部」。</p>
     *
     * @param bo 导出入参（类型、筛选条件、字段清单、用途）
     * @return 导出任务ID
     */
    Long createExport(TalentExportCreateBo bo);

    /**
     * 导出任务分页列表（不返回对象存储标识，只提供系统内受控下载地址）。
     *
     * @param bo        查询条件，可为空
     * @param pageQuery 分页参数
     * @return 导出任务分页结果
     */
    PageResult<TalentExportTaskVo> queryPage(TalentExportQueryBo bo, PageQuery pageQuery);

    /**
     * 受控下载导出结果文件（<b>用途必填</b>，写审计；过期一律拒绝）。
     *
     * @param taskId   导出任务ID
     * @param purpose  下载用途（为空即拒绝并写 {@code denied} 审计）
     * @param response HTTP 响应（流式输出，不返回任何对象存储地址）
     */
    void download(Long taskId, String purpose, HttpServletResponse response);

}
