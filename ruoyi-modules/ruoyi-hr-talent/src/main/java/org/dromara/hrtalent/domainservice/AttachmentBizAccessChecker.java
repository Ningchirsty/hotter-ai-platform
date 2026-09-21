package org.dromara.hrtalent.domainservice;

/**
 * 附件业务对象资源级鉴权扩展点（设计文档 §8.8「下载前校验业务记录权限」、§15.3）。
 *
 * <p><b>定位</b>：附件服务只负责「登录 + 按钮权限 + 用途必填 + 审计」这些通用闸门；
 * 「这条附件所属的<b>业务记录</b>对当前用户是否可见」必须由拥有该业务数据的领域服务判定，
 * 禁止在附件服务内自行拼装数据范围条件（SPEC-P3 §1「不得在各 Controller 内各自实现授权规则」）。</p>
 *
 * <p><b>实现约定</b>：</p>
 * <ul>
 *     <li>默认实现 {@link DefaultAttachmentBizAccessChecker} 已随本模块提供：统一把业务对象
 *     解析为人才，再走 {@link TalentScopeDomainService} 的可见范围判定；</li>
 *     <li>实现必须<b>不可见时抛 {@link org.dromara.common.core.exception.ServiceException}</b>（中文提示），
 *     可见时正常返回；记录不存在、链路断、业务类型不认识同样一律拒绝；</li>
 *     <li>附件服务按 <b>fail-closed</b> 处理：取不到任何实现时直接拒绝访问，<b>绝不放行</b>
 *     （与 {@link TalentScopeDomainService} 的授权查询异常即拒绝姿态一致）。</li>
 * </ul>
 *
 * @author hr-talent
 */
public interface AttachmentBizAccessChecker {

    /**
     * 校验当前登录用户对指定业务记录是否具备访问附件的权限。
     *
     * @param bizType 业务类型稳定编码（application/interview/background/offer/talent）
     * @param bizId   业务对象ID
     * @throws org.dromara.common.core.exception.ServiceException 业务记录不存在或当前用户无权访问
     */
    void check(String bizType, Long bizId);

}
