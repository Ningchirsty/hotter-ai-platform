/**
 * 数据访问层 Mapper 包。
 * <p>Mapper 继承 {@code BaseMapperPlus<Entity, Vo>}，复杂关联查询按项目现有 MPJ 方式实现。
 * 人才相关 Mapper <b>不使用</b> {@code @DataPermission}，可见范围条件由
 * {@code TalentScopeDomainService} 统一生成（设计文档 §21.5 / §21.14）。</p>
 *
 * @author hr-talent
 */
package org.dromara.hrtalent.mapper;
