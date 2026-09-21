package org.dromara.hrtalent.service.recruitment;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitBackgroundBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitBackgroundQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitBackgroundDetailVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitBackgroundVo;

/**
 * 招聘背调服务接口（SPEC-P3 §2.4、§3.4）。
 *
 * <p><b>敏感字段独立权限</b>：列表与普通详情<b>永不</b>返回 {@code detail_cipher}；
 * 只有 {@link #viewDetail(Long, String)}（对应权限 {@code recruit:background:view-sensitive}）
 * 才会返回明文明细，且必须先写敏感审计、用途为空必须拒绝。</p>
 *
 * <p><b>阶段流转边界</b>：背调服务不写 {@code hr_recruit_application}，阶段流转由应聘服务负责；
 * 结论变化通过 {@code BackgroundResultChangedEvent} 通知（或由对方轮询）。</p>
 *
 * @author hr-talent
 */
public interface IRecruitBackgroundService {

    /**
     * 分页查询背调记录（不含敏感明细）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 背调分页结果
     */
    PageResult<RecruitBackgroundVo> queryPage(RecruitBackgroundQueryBo bo, PageQuery pageQuery);

    /**
     * 获取背调普通详情（不含敏感明细）。
     *
     * @param backgroundId 背调记录ID
     * @return 背调普通详情
     */
    RecruitBackgroundVo getDetail(Long backgroundId);

    /**
     * 新增背调记录。
     *
     * <p>同一应聘记录只保留一条当前有效背调：新增时会把该应聘记录下已有的有效背调置为
     * {@code cancelled}（因 DDL 无 {@code current_flag}，复用状态编码表达「已被替代」，见交付说明）。</p>
     *
     * @param bo 背调入参
     * @return 新增的背调记录ID
     */
    Long create(RecruitBackgroundBo bo);

    /**
     * 更新背调记录（不支持修改所属应聘记录）。
     *
     * @param bo 背调入参，必须携带 {@code backgroundId}
     */
    void update(RecruitBackgroundBo bo);

    /**
     * 查看背调敏感明细。
     *
     * <p>调用方必须持有 {@code recruit:background:view-sensitive}；服务层先写
     * {@code background_view} 审计（用途为空记 {@code denied} 并抛中文提示），
     * 审计成功后才返回明细。</p>
     *
     * @param backgroundId 背调记录ID
     * @param purpose      查看用途/事由，必填
     * @return 含明文敏感说明的背调明细
     */
    RecruitBackgroundDetailVo viewDetail(Long backgroundId, String purpose);

}
