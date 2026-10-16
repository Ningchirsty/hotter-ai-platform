import { describe, expect, it } from 'vitest';
import { foldId, needsFold } from './id';

/**
 * 长 ID 折叠（走读 §5.5）。
 *
 * <p>重点钉两件：①19 位雪花 ID 折叠后**头尾都要保留**（人靠头尾对行）；
 * ②短 ID **不折叠**（"1…2"这种折叠反而更难读）。</p>
 */
describe('aig long id', () => {
  it('★ 雪花 ID 折叠成"头 6 位…尾 6 位"，头尾都保留', () => {
    const snowflake = '2109914104600293377';
    expect(foldId(snowflake)).toBe('210991…293377');
    expect(foldId(snowflake, 4)).toBe('2109…3377');
  });

  it('短 ID / 空值不折叠（折叠了反而更难读）', () => {
    expect(foldId('12345')).toBe('12345');
    expect(foldId('1234567890123')).toBe('1234567890123');
    expect(foldId('')).toBe('');
    expect(foldId(null)).toBe('');
    expect(foldId(undefined)).toBe('');
  });

  it('数字与字符串同样处理；字符串 ID 必须原样保留（大 ID 用字符串传）', () => {
    expect(foldId('1761000000000000100')).toBe('176100…000100');
    expect(foldId(123456)).toBe('123456');
  });

  it('needsFold 与 foldId 口径一致（只有真折叠了才显示复制图标）', () => {
    expect(needsFold('2109914104600293377')).toBe(true);
    expect(needsFold('12345')).toBe(false);
    expect(needsFold(null)).toBe(false);
    expect(needsFold('  2109914104600293377  ')).toBe(true);
  });
});
