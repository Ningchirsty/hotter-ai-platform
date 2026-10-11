package org.dromara.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.CpTaskFile;
import org.dromara.content.domain.vo.CpTaskFileVo;

import java.util.List;
import java.util.Map;

/**
 * 任务附件 Mapper 接口
 *
 * @author content
 */
@Mapper
public interface CpTaskFileMapper extends BaseMapperPlus<CpTaskFile, CpTaskFileVo> {

    /**
     * 「我的附件」：所属任务的负责人是我的那些附件（员工门户跨域聚合用）。
     *
     * <p><b>归属取"任务负责人"而不是附件自己的 create_by</b>：附件经常由系统或协作方上传，
     * 它的 create_by 是提交动作的人，不是这件事的责任人。内容域的"我的"以
     * {@code cp_task.owner_id}（任务负责人）为准——与内容域自己的列表查询同一口径。
     * 两处 {@code del_flag} 都要判：内容域的删除值是 {@code '1'}，与图片/视频域的 {@code '2'} 不同。</p>
     *
     * @param ownerId 任务负责人ID（由登录态取得）
     * @param limit   最多返回几条
     * @return 行（file_id/task_id/file_name/file_kind/file_size/create_time）
     */
    @Select("""
        SELECT f.file_id AS file_id, f.task_id AS task_id, f.file_name AS file_name,
               f.file_kind AS file_kind, f.file_size AS file_size, f.create_time AS create_time
          FROM cp_task_file f
          JOIN cp_task t ON t.task_id = f.task_id
         WHERE t.owner_id = #{ownerId}
           AND f.del_flag = '0'
           AND t.del_flag = '0'
         ORDER BY f.file_id DESC
         LIMIT #{limit}
        """)
    List<Map<String, Object>> listOwnedAssetRows(@Param("ownerId") Long ownerId, @Param("limit") int limit);

}

