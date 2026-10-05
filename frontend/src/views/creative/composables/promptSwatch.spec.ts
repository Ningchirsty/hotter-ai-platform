import { describe, expect, it } from 'vitest';

import { promptColorTokens, splitPromptColors } from './promptSwatch';

/**
 * 提示词色号切分的口径（v1 反馈 视觉基因 1.2「不要展示编号最好」）。
 *
 * 这一层只决定"页面上怎么显示"，但**切错就会少字/多字**——而这段文本是出图下发的依据，
 * 所以边界（词内相邻、长度不合法、无空格、结尾）都要钉住。
 */
describe('splitPromptColors', () => {
  it('把提示词按色号切成 文字/色号 交替的片段，且拼回来等于原文', () => {
    const text = '配色：主色 #C8443C、辅色 #F2E8E6；背景 #F5F5F3';
    const segs = splitPromptColors(text);
    expect(segs.map((s) => s.kind)).toEqual([
      'text',
      'color',
      'text',
      'color',
      'text',
      'color'
    ]);
    expect(segs.filter((s) => s.kind === 'color').map((s) => s.text)).toEqual([
      '#C8443C',
      '#F2E8E6',
      '#F5F5F3'
    ]);
    // 关键不变式：所有片段拼起来必须与原文逐字相同（显示层不许改文本）
    expect(segs.map((s) => s.text).join('')).toBe(text);
  });

  it('没有色号时是一整段文字（不产生空片段）', () => {
    expect(splitPromptColors('一片柔光，产品居中')).toEqual([
      { kind: 'text', text: '一片柔光，产品居中' }
    ]);
  });

  it('空输入返回空数组（不是含空串的一段）', () => {
    expect(splitPromptColors('')).toEqual([]);
    expect(splitPromptColors(null)).toEqual([]);
    expect(splitPromptColors(undefined)).toEqual([]);
  });

  it('色号紧贴中文/标点也能切开（无空格是常态）', () => {
    const segs = splitPromptColors('主色#C8443C，辅色#F2E8E6。');
    expect(segs.map((s) => s.text).join('')).toBe('主色#C8443C，辅色#F2E8E6。');
    expect(segs.filter((s) => s.kind === 'color')).toHaveLength(2);
  });

  it('3/4/6/8 位都认；长度不合法的（#12345）当普通文字，不猜颜色', () => {
    expect(promptColorTokens('#abc #abcd #aabbcc #aabbccdd')).toEqual([
      '#abc',
      '#abcd',
      '#aabbcc',
      '#aabbccdd'
    ]);
    const weird = splitPromptColors('编号 #12345 不是色号');
    expect(weird).toEqual([{ kind: 'text', text: '编号 #12345 不是色号' }]);
    expect(promptColorTokens('编号 #12345 不是色号')).toEqual([]);
  });

  it('同一个色号出现多次，去重后按首次出现顺序给出', () => {
    expect(promptColorTokens('#F5F5F3 + #C8443C + #F5F5F3')).toEqual(['#F5F5F3', '#C8443C']);
  });

  it('结尾是色号时不会吞掉或补出字符', () => {
    expect(splitPromptColors('背景 #F5F5F3')).toEqual([
      { kind: 'text', text: '背景 ' },
      { kind: 'color', text: '#F5F5F3' }
    ]);
  });

  it('连续两个色号之间不插入空文字片段', () => {
    expect(splitPromptColors('#C8443C#F2E8E6')).toEqual([
      { kind: 'color', text: '#C8443C' },
      { kind: 'color', text: '#F2E8E6' }
    ]);
  });
});
