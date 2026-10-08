import { describe, expect, it } from 'vitest';
import type { GeneratedInspiration } from '../../api/image/inspiration';
import type { CloudImageModelsVO } from '../../api/image/types';
import { generatedWork, generatedRoutes } from './generated-pool';
import { isRouteAvailable } from './types';

const item: GeneratedInspiration = { id: '9007199254740993', assetId: '9007199254740993', taskId: '123', taskNo: 'IMAGE-123', title: '花瓶', model: 'gpt-image-2.5-flare', capability: 'EDIT', prompt: 'cobalt blue vase', source: 'bluocto', contentType: 'image/png', createdAt: '2026-10-08' };
const status: CloudImageModelsVO = { configured: true, models: ['gpt-image-2.5-sunburst', 'gpt-image-2.5-flare', 'unverified'], capabilities: [], verified: true,
  profiles: [{ model: 'gpt-image-2.5-flare', testedAt: '', capabilities: [{ code: 'EDIT', verified: true, status: 'PASSED' }] }, { model: 'gpt-image-2.5-sunburst', testedAt: '', capabilities: [{ code: 'T2I', verified: true, status: 'PASSED' }] }] };
describe('真实外部作品推荐', () => {
  it('保留真实素材、来源、描述和 ID，未生成截图模板', () => {
    const work = generatedWork(item);
    expect(work.assetId).toBe(item.assetId); expect(work.provenance?.prompt).toBe(item.prompt);
    expect(work.category).toBe('产品设计'); expect(work.routes).toEqual([]);
  });
  it('优先推荐作品原模型，仅允许服务端已验收能力', () => {
    const routes = generatedRoutes(generatedWork(item), status, []);
    expect(routes.map(route => route.model)).toEqual(['gpt-image-2.5-flare', 'gpt-image-2.5-sunburst']);
    expect(routes[0].capability).toBe('EDIT'); expect(routes[1].capability).toBe('T2I');
    expect(routes.every(route => isRouteAvailable(route, [], status))).toBe(true);
    expect(routes.every(route => !isRouteAvailable(route, [], { ...status, configured: false }))).toBe(true);
  });
  it('云端不可用时仍可带入真实发布的本地工作流', () => {
    const flows = [{ workflowCode: 'wf-t2i-qwen21', modelCode: 'Qwen', capabilityCode: 'T2I', submittable: true, status: 'PUBLISHED' }];
    const routes = generatedRoutes(generatedWork(item), undefined, flows);
    expect(routes).toHaveLength(1); expect(routes[0].source).toBe('local'); expect(routes[0].prompt).toBe(item.prompt);
    expect(generatedRoutes(generatedWork(item), undefined, [{ ...flows[0], status: 'DRAFT' }])).toEqual([]);
  });
});
