package org.dromara.aigov.task.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.task.domain.bo.AigTaskMirrorQueryBo;
import org.dromara.aigov.task.domain.vo.AigTaskMirrorSourceVo;
import org.dromara.aigov.task.domain.vo.AigTaskMirrorVo;
import org.dromara.aigov.task.service.IAigTaskMirrorService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统一任务视图的「存量只读镜像」接口。
 *
 * <p><b>权限刻意只复用只读标识</b>（{@code aig:task:list} / {@code aig:task:query}）：
 * 这一组接口按定义不含任何写操作（{@code AigTaskMirrorVo#isReadOnly()} 恒为 true，
 * SPI 也没有写方法），因此不该新增权限、更不该顺带要求 {@code aig:task:operate}。
 * 对存量记录的重试/选定/质检仍走来源自己的接口与权限——那是各业务域的口径，
 * 治理层替它们决定权限边界是不对的。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/task/mirror")
public class AigTaskMirrorController {

    private final IAigTaskMirrorService mirrorService;

    /**
     * 可用的镜像来源清单。
     *
     * @return 来源列表
     */
    @SaCheckPermission(AigConstants.PERM_TASK_LIST)
    @GetMapping("/sources")
    public R<List<AigTaskMirrorSourceVo>> sources() {
        return R.ok(mirrorService.listSources());
    }

    /**
     * 分页查询某个来源的镜像行。
     *
     * @param bo        查询条件（来源必填）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_TASK_LIST)
    @GetMapping("/list")
    public R<PageResult<AigTaskMirrorVo>> list(
        @Validated({Default.class, QueryGroup.class}) AigTaskMirrorQueryBo bo, PageQuery pageQuery) {
        return R.ok(mirrorService.queryPage(bo, pageQuery));
    }

    /**
     * 取某个来源的单行详情。
     *
     * @param source 来源编码
     * @param refId  来源侧主键
     * @return 镜像行（不存在时 data 为 null）
     */
    @SaCheckPermission(AigConstants.PERM_TASK_QUERY)
    @GetMapping("/{source}/{refId}")
    public R<AigTaskMirrorVo> detail(@PathVariable("source") String source,
                                     @PathVariable("refId") String refId) {
        return R.ok(mirrorService.getDetail(source, refId));
    }

}
