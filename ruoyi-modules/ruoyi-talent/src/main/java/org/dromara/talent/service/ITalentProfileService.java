package org.dromara.talent.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.talent.domain.bo.TlTalentArchiveBo;
import org.dromara.talent.domain.bo.TlTalentBo;
import org.dromara.talent.domain.bo.TlTalentContactBo;
import org.dromara.talent.domain.bo.TlTalentQueryBo;
import org.dromara.talent.domain.vo.TlTalentContactVo;
import org.dromara.talent.domain.vo.TlTalentDetailVo;
import org.dromara.talent.domain.vo.TlTalentDuplicateVo;
import org.dromara.talent.domain.vo.TlTalentVo;

import java.util.List;

/**
 * 人才主档服务。
 * <p>
 * 所有以 ID 访问的方法都必须先加载对象并执行区域 / 单条授权校验（见
 * {@link org.dromara.talent.helper.TalentScopeHelper}）。
 *
 * @author talent
 */
public interface ITalentProfileService {

    /**
     * 分页查询人才台账（强制追加服务端可见区域范围）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<TlTalentVo> queryPage(TlTalentQueryBo bo, PageQuery pageQuery);

    /**
     * 人才详情（含附件、联系记录、当前用户权限标记）。
     *
     * @param talentId 人才ID
     * @return 详情
     */
    TlTalentDetailVo getDetail(Long talentId);

    /**
     * 返回完整手机号明文；内部写 VIEW_FULL_PHONE 审计。
     *
     * @param talentId 人才ID
     * @return 手机号明文
     */
    String getFullPhone(Long talentId);

    /**
     * 新建；执行重复预检。若命中且 {@code bo.duplicateConfirmed} 不为 true，
     * 抛 {@code ServiceException} 提示前端改调 {@link #preCheck(TlTalentBo)}。
     *
     * @param bo 人才信息
     * @return 新人才ID
     */
    Long create(TlTalentBo bo);

    /**
     * 编辑人才档案。
     *
     * @param bo 人才信息
     */
    void update(TlTalentBo bo);

    /**
     * 归档人才档案。
     *
     * @param bo 归档参数
     */
    void archive(TlTalentArchiveBo bo);

    /**
     * 重复预检（不落库）：按 phone_hash 满分、姓名 + 后四位弱匹配、姓名 + 同区域。
     *
     * @param bo 人才信息
     * @return 疑似重复清单（仅返回当前用户区域可见的命中）
     */
    List<TlTalentDuplicateVo> preCheck(TlTalentBo bo);

    /**
     * 只写面试 / 联系反馈，不改档案主字段。
     *
     * @param bo 联系记录
     * @return 联系记录ID
     */
    Long addContact(TlTalentContactBo bo);

    /**
     * 人才联系记录列表。
     *
     * @param talentId 人才ID
     * @return 联系记录列表
     */
    List<TlTalentContactVo> listContacts(Long talentId);

}
