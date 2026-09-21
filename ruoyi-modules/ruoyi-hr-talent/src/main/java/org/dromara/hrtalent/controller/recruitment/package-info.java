/**
 * 招聘过程控制器包。
 * <p>P2 落地：需求、公司月度计划、月度结转、岗位、候选人、面试、背调、
 * 招聘渠道、同行信息、招聘标准、数据导入中心、敏感操作审计。
 * 控制器统一返回 {@code R<T>}，并使用 {@code @SaCheckPermission}、{@code @Log}、
 * {@code @RepeatSubmit} 与校验分组（设计文档 §21.2）。</p>
 *
 * @author hr-talent
 */
package org.dromara.hrtalent.controller.recruitment;
