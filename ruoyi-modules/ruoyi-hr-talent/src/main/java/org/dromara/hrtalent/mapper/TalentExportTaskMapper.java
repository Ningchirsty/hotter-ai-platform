package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentExportTask;
import org.dromara.hrtalent.domain.vo.talent.TalentExportTaskVo;

/**
 * 人才导出任务 Mapper（SPEC-P4 §2.6 F 线）。
 *
 * <p><b>说明</b>：导出任务列表只按「导出人 / 类型 / 状态 / 创建时间」等本表字段过滤，
 * 不需要跨表连接，因此<b>不</b>新增自定义 SQL；实体到 VO 的转换统一由
 * {@link BaseMapperPlus#selectVoPage} 完成（服务层不直接调用 {@code MapstructUtils}）。</p>
 *
 * <p><b>可见范围</b>：本表记录的是「谁在什么时候导出了什么」，其本身不承载人才数据可见性；
 * 人才数据的可见范围判定由
 * {@code org.dromara.hrtalent.domainservice.TalentScopeDomainService} 在导出时叠加，
 * 本 Mapper <b>不</b>实现任何授权规则（设计文档 §11.1）。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentExportTaskMapper extends BaseMapperPlus<TalentExportTask, TalentExportTaskVo> {
}
