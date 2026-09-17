package org.dromara.talent.service;

import org.dromara.talent.domain.TlTalentAccessGrant;
import org.dromara.talent.domain.bo.TlAccessGrantBo;

import java.util.List;

/**
 * 单条 / 临时访问授权服务。
 *
 * @author talent
 */
public interface ITalentAccessGrantService {

    /**
     * 某人才的全部有效授权。
     *
     * @param talentId 人才ID
     * @return 授权列表
     */
    List<TlTalentAccessGrant> listByTalent(Long talentId);

    /**
     * 新建授权。
     *
     * @param bo 授权参数
     * @return 授权ID
     */
    Long create(TlAccessGrantBo bo);

    /**
     * 撤销授权（逻辑置 status='1'）。
     *
     * @param grantId 授权ID
     */
    void revoke(Long grantId);

}
