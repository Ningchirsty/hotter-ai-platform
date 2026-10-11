import type { ImageCapabilityCode } from '@/api/image/types';
import type { VideoCapabilityCode } from '@/api/video/types';
import {
  isRouteAvailable,
  type CreativeMedia,
  type ImageInspirationWork,
  type VideoInspirationWork,
  type InspirationRoute,
  type InspirationWork,
  type InspirationWorkflow
} from './types';

export const INSPIRATION_CATEGORIES = [
  '全部',
  '品牌设计',
  '海报与广告',
  '插画',
  'UI设计',
  '角色设计',
  '影片与分镜',
  '产品设计',
  '建筑设计'
];

const IMAGE_WORKFLOWS: Record<ImageCapabilityCode, string> = {
  CONTROL: 'wf-local-image-z-image-turbo-fun-union-controlnet',
  T2I: 'wf-t2i-qwen21',
  I2I: 'wf-i2i-qwen21',
  EDIT: 'wf-edit-qwen21',
  BGREMOVE: 'wf-bgremove-qwen21',
  WHITEBG: 'wf-whitebg-qwen21'
};
const VIDEO_REFERENCE: Record<VideoCapabilityCode, string> = {
  R2V: '请上传两张参考图片，并在描述中使用 <Picture 1> 和 <Picture 2>。',
  T2V: '从文字构思开始，无需上传参考图。',
  I2V: '请在左侧上传你自己的单张参考图，案例封面不会自动作为输入。',
  FL2V: '请在左侧上传你自己的首帧和尾帧图片。'
};
const IMAGE_REFERENCE: Record<ImageCapabilityCode, string> = {
  CONTROL: '请上传结构控制参考图。',
  T2I: '从画面描述开始，无需上传参考图。',
  I2I: '请上传原图；此能力用于整体风格重绘。',
  EDIT: '请上传编辑目标，最多再添加两张参考图。',
  BGREMOVE: '请上传主体图片，提示词由工作流固定。',
  WHITEBG: '请上传产品图，主体保持不变，输出纯白背景。'
};

function image(capability: ImageCapabilityCode, prompt: string, reason: string): InspirationRoute {
  return {
    media: 'image',
    capability,
    workflowCode: IMAGE_WORKFLOWS[capability],
    model: 'Qwen-Image-2.1',
    prompt,
    reason,
    referenceHint: IMAGE_REFERENCE[capability]
  };
}
function video(capability: VideoCapabilityCode, prompt: string, reason: string): InspirationRoute {
  return {
    media: 'video',
    capability,
    workflowCode: `wf-${capability.toLowerCase()}-h3`,
    model: 'MiniMax H3',
    prompt,
    reason,
    referenceHint: VIDEO_REFERENCE[capability]
  };
}

/** 首批精选参考：封面来自用户提供的灵感发现截图，匹配依据为已接入能力。 */
export const IMAGE_INSPIRATION_WORKS: ImageInspirationWork[] = [
  {
    id: 'natural-brand',
    media: 'image',
    title: '自然材质 · 家居品牌视觉',
    category: '品牌设计',
    tags: ['自然光', '暖色调', '材质叙事'],
    cover: { x: 150, y: 252, width: 326, height: 526 },
    routes: [
      image(
        'T2I',
        '家居品牌视觉，纸质灯具与陶瓷杯置于原木桌面，暖棕色背景，柔和侧光，自然材质，干净留白。',
        '文生图适合探索材质、色调与品牌视觉构图。'
      )
    ]
  },
  {
    id: 'pastel-building',
    media: 'image',
    title: '粉色微缩 · 建筑构想',
    category: '建筑设计',
    tags: ['微缩场景', '立体造型', '柔和色彩'],
    cover: { x: 491, y: 252, width: 326, height: 326 },
    routes: [
      image(
        'T2I',
        '粉色微缩建筑模型，欧式楼宇与弧形轨道组合，等距视角，单色背景，柔和影棚光，精致立体场景。',
        '文生图用于探索建筑造型与场景气氛。'
      )
    ]
  },
  {
    id: 'social-ad',
    media: 'image',
    title: '生活场景 · 社交广告',
    category: '海报与广告',
    tags: ['生活方式', '场景构图', '自然表达'],
    cover: { x: 833, y: 252, width: 326, height: 246 },
    routes: [
      image(
        'EDIT',
        '保留 <image1> 的人物与服装，将背景调整为明亮、简洁的生活空间，自然窗光，广告摄影风格。',
        '指令改图适合在已有素材上调整局部场景。'
      )
    ]
  },
  {
    id: 'portrait-scene',
    media: 'image',
    title: '人物近景 · 情绪分镜',
    category: '影片与分镜',
    tags: ['人物近景', '暖光', '情绪分镜'],
    cover: { x: 1174, y: 252, width: 326, height: 246 },
    routes: [
      image(
        'T2I',
        '室内人物近景分镜，暖色灯光，安静自然的表情，浅景深，电影摄影质感。',
        '文生图可先构思人物近景与分镜画面。'
      )
    ]
  },
  {
    id: 'product-layout',
    media: 'image',
    title: '产品叙事 · 多视角广告',
    category: '产品设计',
    tags: ['产品特写', '展示构图', '功能叙事'],
    cover: { x: 1515, y: 252, width: 326, height: 580 },
    routes: [
      image('WHITEBG', '', '白底图适合准备产品展示素材，产品像素保持不变；多视角排版需后续设计。'),
      image('BGREMOVE', '', '抠图去背景可准备透明产品素材，便于后续组合场景与排版。')
    ]
  },
  {
    id: 'interface-story',
    media: 'image',
    title: '信息层级 · 产品页面视觉',
    category: 'UI设计',
    tags: ['留白', '信息层级', '产品展示'],
    cover: { x: 491, y: 640, width: 326, height: 383 },
    routes: [
      image(
        'T2I',
        '极简产品页面视觉概念，白色背景，主产品居中，清晰信息层级，宽松留白，柔和阴影。',
        '文生图用于页面视觉方向探索；不生成可运行的 UI 代码。'
      )
    ]
  },
  {
    id: 'cinematic-character',
    media: 'image',
    title: '城市暮色 · 角色气质',
    category: '角色设计',
    tags: ['电影氛围', '冷暖对比', '角色气质'],
    cover: { x: 1174, y: 572, width: 326, height: 183 },
    routes: [
      image(
        'EDIT',
        '保留 <image1> 的人物特征，背景改为暮色城市，蓝灰色环境光与暖色轮廓光对比，电影海报氛围。',
        '指令改图用于探索既有角色的场景与气氛。'
      )
    ]
  },
  {
    id: 'soft-illustration',
    media: 'image',
    title: '静物造型 · 插画表达',
    category: '插画',
    tags: ['几何构图', '柔和质感', '风格转换'],
    cover: { x: 833, y: 572, width: 326, height: 405 },
    routes: [
      image(
        'I2I',
        '整体重绘为柔和的立体插画风格，保留原图构图与主体位置，简洁几何形状，自然纸张与木材质感。',
        '图生图适合整体风格转换，重绘幅度可调。'
      )
    ]
  },
  {
    id: 'minimal-campaign',
    media: 'image',
    title: '留白配色 · 品牌海报',
    category: '海报与广告',
    tags: ['极简', '配色', '视觉节奏'],
    cover: { x: 1174, y: 831, width: 326, height: 199 },
    routes: [
      image(
        'T2I',
        '极简品牌海报，两件浅粉色产品置于米白背景，柔和阴影，充足留白，简洁高级的配色。',
        '文生图适合探索海报配色与主体构图。'
      )
    ]
  }
];

/** 公开实拍样例用于镜头参考，不代表平台或模型生成的作品。 */
export const VIDEO_INSPIRATION_WORKS: VideoInspirationWork[] = [
  {
    id: 'video-3574',
    media: 'video',
    title: '咖啡制作 · 手部动作',
    category: '产品广告',
    tags: ['制作过程', '细节特写', '动作节奏'],
    video: {
      src: 'https://assets.mixkit.co/videos/3574/3574-360.mp4',
      poster: 'https://assets.mixkit.co/videos/3574/3574-thumb-720-0.jpg',
      sourceName: 'Mixkit · 示例参考',
      sourceUrl: 'https://mixkit.co/free-stock-video/serving-coffee-in-a-cup-at-a-coffee-shop-3574/'
    },
    routes: [
      video(
        'I2V',
        '咖啡机前的产品特写，手部轻缓操作，镜头稳定，突出金属与咖啡液的质感。',
        '参考这个视频的镜头、构图与运动方向；按当前工作流能力尝试相近的创作表达。'
      )
    ]
  },
  {
    id: 'video-43941',
    media: 'video',
    title: '暖色咖啡 · 产品特写',
    category: '产品广告',
    tags: ['暖色调', '产品特写', '液体流动'],
    video: {
      src: 'https://assets.mixkit.co/videos/43941/43941-360.mp4',
      poster: 'https://assets.mixkit.co/videos/43941/43941-thumb-720-0.jpg',
      sourceName: 'Mixkit · 示例参考',
      sourceUrl: 'https://mixkit.co/free-stock-video/pouring-coffee-in-a-cup-43941/'
    },
    routes: [
      video(
        'I2V',
        '暖色静物场景中，咖啡缓慢倒入杯中，镜头轻微推进，背景简洁，主体稳定。',
        '参考这个视频的镜头、构图与运动方向；按当前工作流能力尝试相近的创作表达。'
      )
    ]
  },
  {
    id: 'video-43951',
    media: 'video',
    title: '咖啡豆 · 材质镜头',
    category: '材质与光影',
    tags: ['材质纹理', '微距构图', '缓慢运动'],
    video: {
      src: 'https://assets.mixkit.co/videos/43951/43951-360.mp4',
      poster: 'https://assets.mixkit.co/videos/43951/43951-thumb-720-0.jpg',
      sourceName: 'Mixkit · 示例参考',
      sourceUrl: 'https://mixkit.co/free-stock-video/texture-of-a-surface-covered-in-coffee-beans-43951/'
    },
    routes: [
      video(
        'T2V',
        '咖啡豆表面的近距离镜头，镜头缓慢横移，柔和侧光，突出自然材质和表面层次。',
        '参考这个视频的镜头、构图与运动方向；按当前工作流能力尝试相近的创作表达。'
      )
    ]
  },
  {
    id: 'video-223',
    media: 'video',
    title: '咖啡馆 · 人物叙事',
    category: '人物叙事',
    tags: ['生活方式', '人物近景', '自然动作'],
    video: {
      src: 'https://assets.mixkit.co/videos/223/223-360.mp4',
      poster: 'https://assets.mixkit.co/videos/223/223-thumb-720-0.jpg',
      sourceName: 'Mixkit · 示例参考',
      sourceUrl: 'https://mixkit.co/free-stock-video/woman-drinking-coffee-in-a-cafe-223/'
    },
    routes: [
      video(
        'FL2V',
        '咖啡馆中的人物近景，从安静阅读的首帧过渡到端起咖啡的尾帧，动作自然，光线连续。',
        '参考这个视频的镜头、构图与运动方向；按当前工作流能力尝试相近的创作表达。'
      )
    ]
  },
  {
    id: 'video-4385',
    media: 'video',
    title: '餐厅空间 · 环境分镜',
    category: '空间与建筑',
    tags: ['空间层次', '环境全景', '生活氛围'],
    video: {
      src: 'https://assets.mixkit.co/videos/4385/4385-360.mp4',
      poster: 'https://assets.mixkit.co/videos/4385/4385-thumb-720-0.jpg',
      sourceName: 'Mixkit · 示例参考',
      sourceUrl: 'https://mixkit.co/free-stock-video/customers-in-a-minimalist-style-restaurant-4385/'
    },
    routes: [
      video(
        'T2V',
        '简洁餐厅的环境全景，镜头缓慢移动，人物作为背景轻微活动，保留空间层次和自然光。',
        '参考这个视频的镜头、构图与运动方向；按当前工作流能力尝试相近的创作表达。'
      )
    ]
  },
  {
    id: 'video-2213',
    media: 'video',
    title: '林间瀑布 · 自然流动',
    category: '自然风景',
    tags: ['水流', '自然景观', '环境氛围'],
    video: {
      src: 'https://assets.mixkit.co/videos/2213/2213-360.mp4',
      poster: 'https://assets.mixkit.co/videos/2213/2213-thumb-720-0.jpg',
      sourceName: 'Mixkit · 示例参考',
      sourceUrl: 'https://mixkit.co/free-stock-video/waterfall-in-forest-2213/'
    },
    routes: [
      video(
        'I2V',
        '林间瀑布场景，水流自然落下，镜头稳定缓慢推进，树叶轻微晃动，柔和自然光。',
        '参考这个视频的镜头、构图与运动方向；按当前工作流能力尝试相近的创作表达。'
      )
    ]
  },
  {
    id: 'video-41576',
    media: 'video',
    title: '山路行进 · 镜头运动',
    category: '镜头运动',
    tags: ['第一视角', '场景连续', '运动方向'],
    video: {
      src: 'https://assets.mixkit.co/videos/41576/41576-360.mp4',
      poster: 'https://assets.mixkit.co/videos/41576/41576-thumb-720-0.jpg',
      sourceName: 'Mixkit · 示例参考',
      sourceUrl: 'https://mixkit.co/free-stock-video/going-down-a-curved-highway-through-a-mountain-range-41576/'
    },
    routes: [
      video(
        'FL2V',
        '从山路的首帧平滑向前行进至尾帧，保持道路方向与山体连贯，运动平稳，日光自然。',
        '参考这个视频的镜头、构图与运动方向；按当前工作流能力尝试相近的创作表达。'
      )
    ]
  },
  {
    id: 'video-4075',
    media: 'video',
    title: '晴日草地 · 氛围短片',
    category: '自然风景',
    tags: ['阳光', '草地', '自然色彩'],
    video: {
      src: 'https://assets.mixkit.co/videos/4075/4075-360.mp4',
      poster: 'https://assets.mixkit.co/videos/4075/4075-thumb-720-0.jpg',
      sourceName: 'Mixkit · 示例参考',
      sourceUrl: 'https://mixkit.co/free-stock-video/countryside-meadow-4075/'
    },
    routes: [
      video(
        'T2V',
        '晴日乡间草地，柔和阳光，草叶与小花随风轻微晃动，镜头缓慢拉远，宁静氛围。',
        '参考这个视频的镜头、构图与运动方向；按当前工作流能力尝试相近的创作表达。'
      )
    ]
  },
  {
    id: 'video-51585',
    media: 'video',
    title: '溪流航拍 · 场景过渡',
    category: '镜头运动',
    tags: ['航拍视角', '空间纵深', '场景过渡'],
    video: {
      src: 'https://assets.mixkit.co/videos/51585/51585-360.mp4',
      poster: 'https://assets.mixkit.co/videos/51585/51585-thumb-720-0.jpg',
      sourceName: 'Mixkit · 示例参考',
      sourceUrl:
        'https://mixkit.co/free-stock-video/flying-over-a-relaxing-creek-full-of-rock-on-the-countryside-51585/'
    },
    routes: [
      video(
        'FL2V',
        '从溪流上方的首帧平滑前进至尾帧，沿溪流方向移动，保持石块与水面层次，轻缓航拍镜头。',
        '参考这个视频的镜头、构图与运动方向；按当前工作流能力尝试相近的创作表达。'
      )
    ]
  }
];

export const INSPIRATION_WORKS: InspirationWork[] = [...IMAGE_INSPIRATION_WORKS, ...VIDEO_INSPIRATION_WORKS];
export const VIDEO_INSPIRATION_CATEGORIES = [
  '全部',
  '产品广告',
  '材质与光影',
  '人物叙事',
  '空间与建筑',
  '自然风景',
  '镜头运动'
];
export const INSPIRATION_PAGE_SIZE = 6;

export function categoriesFor(media: CreativeMedia): string[] {
  return media === 'video' ? VIDEO_INSPIRATION_CATEGORIES : INSPIRATION_CATEGORIES;
}

export interface DiscoveryFilters {
  category?: string;
  keyword?: string;
  model?: string;
  capability?: string;
  savedIds?: string[];
}

export function matchingRoutes(
  work: InspirationWork,
  media: CreativeMedia,
  workflows: InspirationWorkflow[],
  model = '',
  capability = ''
): InspirationRoute[] {
  return routesFor(work, media).filter(
    route =>
      isRouteAvailable(route, workflows) &&
      (!model || route.model === model) &&
      (!capability || route.capability === capability)
  );
}

/** 案例类型、已发布能力和用户筛选共同决定推荐，失效模型不混入推荐。 */
export function recommendedWorks(
  works: InspirationWork[],
  media: CreativeMedia,
  workflows: InspirationWorkflow[],
  filters: DiscoveryFilters = {}
): InspirationWork[] {
  const keyword = (filters.keyword || '').trim().toLowerCase();
  return works.filter(
    work =>
      work.media === media &&
      matchingRoutes(work, media, workflows, filters.model, filters.capability).length > 0 &&
      (!filters.category || filters.category === '全部' || work.category === filters.category) &&
      (!filters.savedIds || filters.savedIds.includes(work.id)) &&
      [work.title, work.category, ...work.tags].join(' ').toLowerCase().includes(keyword)
  );
}

export function paginateWorks(works: InspirationWork[], page: number, pageSize = INSPIRATION_PAGE_SIZE) {
  const size = Math.max(1, Math.floor(pageSize));
  const pageCount = Math.max(1, Math.ceil(works.length / size));
  const currentPage = Math.min(pageCount, Math.max(1, Math.floor(page) || 1));
  return {
    currentPage,
    pageCount,
    total: works.length,
    items: works.slice((currentPage - 1) * size, currentPage * size)
  };
}

export function routesFor(work: InspirationWork, media: CreativeMedia): InspirationRoute[] {
  return work.media === media ? work.routes.filter(route => route.media === media) : [];
}
