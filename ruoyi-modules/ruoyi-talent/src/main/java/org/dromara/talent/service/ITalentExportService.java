package org.dromara.talent.service;

import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.talent.domain.bo.TlExportCreateBo;
import org.dromara.talent.domain.vo.TlExportTaskVo;

/**
 * 人才台账异步导出服务。
 *
 * @author talent
 */
public interface ITalentExportService {

    /**
     * 创建导出任务（同步落库 + 提交异步执行）。
     *
     * @param bo 导出参数
     * @return 导出任务ID
     */
    Long createExport(TlExportCreateBo bo);

    /**
     * 我的导出任务分页。
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<TlExportTaskVo> queryMyPage(TlExportTaskVo query, PageQuery pageQuery);

    /**
     * 受控下载导出文件。
     *
     * @param exportId 导出任务ID
     * @param response HTTP 响应
     */
    void download(Long exportId, HttpServletResponse response);

    /**
     * 异步执行：查询快照 → 生成 Excel（ExcelBuilder.toStream）→ 上传 OSS → 更新任务状态。
     * <p>
     * 该方法带 {@code @Async}，只能通过 Spring 代理调用；实现类内部一律使用
     * {@code SpringUtils.getAopProxy(this).runExportAsync(id)} 触发，避免自调用导致异步失效。
     *
     * @param exportId 导出任务ID
     */
    void runExportAsync(Long exportId);

}
