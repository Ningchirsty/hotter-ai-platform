import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import type { RecruitImportBatchVO, RecruitImportPreviewVO, RecruitImportResultVO } from './types';
import request from '@/utils/request';

/**
 * 招聘数据导入 接口封装。
 *
 * 导入端点挂在各业务页面下（`/recruit/standards`、`/recruit/plans`），
 * 但三段式流程完全一致，故用同一个工厂按 basePath 生成，避免两处各写一遍、
 * 哪天改了一处忘了另一处。
 *
 * @param basePath 业务接口前缀，如 `/recruit/standards`
 */
export function createRecruitImportApi(basePath: string) {
  return {
    /** 上传并预检（返回批次ID与逐行问题清单，不写业务数据） */
    importPreview(file: File): AxiosPromise<RecruitImportPreviewVO> {
      const formData = new FormData();
      formData.append('file', file);
      return request({
        url: `${basePath}/importPreview`,
        method: 'post',
        data: formData,
        // 预检要解析整个文件并查组织机构，给足超时；默认 50s 在慢链路上容易不够
        timeout: 120000
      });
    },

    /** 确认导入（仅导入无 error 的行） */
    importConfirm(batchId: string | number): AxiosPromise<RecruitImportResultVO> {
      return request({
        url: `${basePath}/importConfirm`,
        method: 'post',
        params: { batchId },
        timeout: 180000
      });
    },

    /** 取消批次（不产生任何业务数据） */
    importCancel(batchId: string | number) {
      return request({
        url: `${basePath}/importCancel`,
        method: 'post',
        params: { batchId }
      });
    },

    /** 导入批次分页（追溯来源） */
    listImportBatches(query: { pageNum: number; pageSize: number }): AxiosPromise<PageResult<RecruitImportBatchVO>> {
      return request({
        url: `${basePath}/importBatches`,
        method: 'get',
        params: query
      });
    }
  };
}
