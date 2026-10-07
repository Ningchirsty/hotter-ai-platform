import { describe, expect, it } from 'vitest';
import { IMAGE_MODULES } from '../../views/image/modules';
import { VIDEO_MODULES, resolveWorkflowCode } from '../../views/video/modules';
import {
  INSPIRATION_CATEGORIES,
  INSPIRATION_WORKS,
  matchingRoutes,
  paginateWorks,
  recommendedWorks,
  routesFor
} from './catalog';
import { canSubmitLocal, isRouteAvailable, type InspirationWorkflow } from './types';

describe('灵感方向接回现有能力', () => {
  it('每个分类都有作品，且推荐方向必须对应真实模块契约', () => {
    for (const category of INSPIRATION_CATEGORIES.slice(1)) {
      expect(
        INSPIRATION_WORKS.some(work => work.category === category),
        category
      ).toBe(true);
    }
    for (const work of INSPIRATION_WORKS) {
      for (const route of work.routes) {
        if (route.media === 'image') {
          const module = IMAGE_MODULES.find(item => item.code === route.capability);
          expect(module?.workflowCode).toBe(route.workflowCode);
          if (module?.fields.includes('prompt')) expect(route.prompt.trim()).not.toBe('');
          else expect(route.prompt).toBe('');
        } else {
          const module = VIDEO_MODULES.find(item => item.code === route.capability);
          expect(module).toBeDefined();
          expect(resolveWorkflowCode(module!, 'H3')).toBe(route.workflowCode);
          expect(route.prompt.trim()).not.toBe('');
        }
      }
    }
  });

  it('云端不会借用本地工作流提交，缺失或未发布能力不会提交', () => {
    expect(canSubmitLocal('cloud', { status: 'PUBLISHED', submittable: true })).toBe(false);
    expect(canSubmitLocal('local', undefined)).toBe(false);
    expect(canSubmitLocal('local', { status: 'PUBLISHED', submittable: false })).toBe(false);
    expect(canSubmitLocal('local', { status: 'TESTING', submittable: true })).toBe(false);
    expect(canSubmitLocal('local', { status: 'PUBLISHED', submittable: true })).toBe(true);
  });

  it('图像页不会推荐视频工作流，视频页不会推荐图像工作流', () => {
    for (const work of INSPIRATION_WORKS) {
      expect(routesFor(work, 'image').every(route => route.media === 'image')).toBe(true);
      expect(routesFor(work, 'video').every(route => route.media === 'video')).toBe(true);
    }
  });

  const route = INSPIRATION_WORKS[0].routes[0];
  const published: InspirationWorkflow = {
    workflowCode: route.workflowCode,
    capabilityCode: route.capability,
    status: 'PUBLISHED',
    submittable: true
  };
  it('只允许服务端已发布且可提交的准确工作流进入创作', () => {
    expect(isRouteAvailable(route, [published])).toBe(true);
    expect(isRouteAvailable(route, [])).toBe(false);
    expect(isRouteAvailable(route, [{ ...published, submittable: false }])).toBe(false);
    expect(isRouteAvailable(route, [{ ...published, workflowCode: 'another-workflow' }])).toBe(false);
    expect(isRouteAvailable(route, [{ ...published, capabilityCode: 'another-capability' }])).toBe(false);
    for (const status of ['DRAFT', 'TESTING', 'RETIRED']) {
      expect(isRouteAvailable(route, [{ ...published, status }])).toBe(false);
    }
  });
});

describe('媒体库与模型推荐分页', () => {
  const available: InspirationWorkflow[] = [
    ...new Map(
      INSPIRATION_WORKS.flatMap(work => work.routes).map(route => [
        route.workflowCode,
        {
          workflowCode: route.workflowCode,
          capabilityCode: route.capability,
          status: 'PUBLISHED',
          submittable: true
        }
      ])
    ).values()
  ];

  it('视频与图像必须具有各自的参考媒体，跨媒体案例不能被推荐', () => {
    expect(new Set(INSPIRATION_WORKS.map(work => work.id)).size).toBe(INSPIRATION_WORKS.length);
    for (const work of INSPIRATION_WORKS) {
      expect(work.routes.every(route => route.media === work.media)).toBe(true);
      if (work.media === 'video') {
        expect(work.video.src).toMatch(/^https:\/\/.*\.mp4$/);
        expect(work.video.poster).toBeTruthy();
        expect(work.video.sourceUrl).toBeTruthy();
        expect(routesFor(work, 'image')).toEqual([]);
      } else {
        expect(work.cover.width).toBeGreaterThan(0);
        expect(routesFor(work, 'video')).toEqual([]);
      }
    }
    for (const media of ['image', 'video'] as const) {
      const recommendations = recommendedWorks(INSPIRATION_WORKS, media, available);
      expect(recommendations.length).toBeGreaterThan(6);
      expect(recommendations.every(work => work.media === media)).toBe(true);
    }
  });

  it('模型与能力筛选只返回可用方向，发布撤回后推荐立即收敛', () => {
    const videos = recommendedWorks(INSPIRATION_WORKS, 'video', available, { model: 'MiniMax H3', capability: 'I2V' });
    expect(videos).toHaveLength(3);
    expect(videos.every(work => matchingRoutes(work, 'video', available, 'MiniMax H3', 'I2V').length > 0)).toBe(true);
    expect(recommendedWorks(INSPIRATION_WORKS, 'video', available, { model: 'Qwen-Image-2.1' })).toEqual([]);
    expect(recommendedWorks(INSPIRATION_WORKS, 'image', available, { capability: 'I2V' })).toEqual([]);
    expect(
      recommendedWorks(
        INSPIRATION_WORKS,
        'video',
        available.map(workflow => ({ ...workflow, status: 'RETIRED' }))
      )
    ).toEqual([]);
    expect(recommendedWorks(INSPIRATION_WORKS, 'video', [])).toEqual([]);
  });

  it('多页内容不重复；筛选缩减或收藏清空时越界页收敛', () => {
    const images = recommendedWorks(INSPIRATION_WORKS, 'image', available);
    const first = paginateWorks(images, 1);
    const second = paginateWorks(images, 2);
    expect(first.pageCount).toBe(2);
    expect(first.items).toHaveLength(6);
    expect(second.items).toHaveLength(3);
    expect(second.items.some(work => first.items.includes(work))).toBe(false);
    const filtered = recommendedWorks(INSPIRATION_WORKS, 'image', available, { keyword: '自然材质' });
    expect(paginateWorks(filtered, 2).currentPage).toBe(1);
    expect(paginateWorks(recommendedWorks(INSPIRATION_WORKS, 'image', available, { savedIds: [] }), 2).items).toEqual(
      []
    );
    expect(paginateWorks(images, 99).currentPage).toBe(2);
    expect(paginateWorks(images, 0).currentPage).toBe(1);
  });
});
