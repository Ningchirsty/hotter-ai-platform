import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';
import * as vue from 'vue';

// Run the actual compiled component with Vue's renderer; no browser or paid API is needed.
type Node = { type: string; text: string; props: Record<string, any>; children: Node[]; parent?: Node };
const node = (type: string, text = ''): Node => ({ type, text, props: {}, children: [] });
const renderer = vue.createRenderer<Node, Node>({
  createElement: type => node(type), createText: text => node('#text', text), createComment: text => node('#comment', text),
  setText: (n, text) => { n.text = text; }, setElementText: (n, text) => { n.text = text; n.children = []; },
  patchProp: (n, key, _old, value) => { n.props[key] = value; },
  insert(n, parent, anchor) { if (n.parent) n.parent.children.splice(n.parent.children.indexOf(n), 1); n.parent = parent; const index = anchor ? parent.children.indexOf(anchor) : -1; parent.children.splice(index < 0 ? parent.children.length : index, 0, n); },
  remove(n) { if (n.parent) n.parent.children.splice(n.parent.children.indexOf(n), 1); },
  parentNode: n => n.parent!, nextSibling: n => n.parent?.children[n.parent.children.indexOf(n) + 1]!
});
const api = Object.fromEntries(['listTemplates', 'getTemplate', 'prepareTemplate', 'prepareVideoTemplate', 'generateTemplate', 'getTemplateGeneration', 'retryTemplateGeneration'].map(key => [key, vi.fn()]));
const fixture = { id: 'poster-one', revision: 3, title: { zh: '极简商品海报', en: 'Poster' }, category: '设计海报', outputSize: '1024x1536', model: 'gpt-image-2.5-sunburst', status: 'published', canGenerate: true,
  variables: [{ key: 'subject', label: { zh: '商品', en: 'Subject' }, required: true, type: 'text' }], promptTemplate: 'Minimal poster of {{subject}}, generous whitespace, editorial typography.', output: { size: '1024x1536', n: 1, quality: 'high' } };
const cover = vue.defineComponent({ props: ['id', 'revision', 'title', 'media'], setup: props => () => vue.h('img', { alt: props.title, 'data-template': props.id }) });
const { descriptor } = parse(readFileSync(new URL('./TemplateDiscovery.vue', import.meta.url), 'utf8'));
const compiled = compileScript(descriptor, { id: 'template-discovery-test', inlineTemplate: true }).content;
const javascript = ts.transpileModule(compiled, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
const exports: { default?: any } = {};
new Function('require', 'exports', javascript)((name: string) => {
  if (name === 'vue') return vue;
  if (name === '@/store/modules/user') return { useUserStore: () => ({ userId: 'test-user' }) };
  if (name === '@/api/image/templates') return api;
  if (name === './template-reuse') return { creationMessage: (message?: string) => message || '' };
  if (name.endsWith('.vue')) return { __esModule: true, default: cover };
  throw new Error('Unexpected component dependency: ' + name);
}, exports);
let app: ReturnType<typeof renderer.createApp>;
let root: Node;
let applied: any[][];
function nodes(n = root): Node[] { return [n, ...n.children.flatMap(child => nodes(child))]; }
function text(n: Node): string { return n.text + n.children.map(text).join(''); }
function button(label: string) { const result = nodes().find(n => n.type === 'button' && text(n) === label); expect(result).toBeDefined(); return result!; }
async function click(label: string) { await button(label).props.onClick(); await vue.nextTick(); }
async function mount(media = 'image') {
  root = node('root'); applied = [];
  app = renderer.createApp(exports.default, { media, onApply: (...args: any[]) => applied.push(args) });
  app.directive('hasPermi', {});
  for (const name of ['el-form', 'el-form-item', 'el-pagination', 'el-select', 'el-option']) app.component(name, { setup: (_props: unknown, { slots }: any) => () => vue.h('section', slots.default?.()) });
  app.component('el-input', { props: ['modelValue'], emits: ['update:modelValue'], setup: (props: any, { emit }: any) => () => vue.h('input', { value: props.modelValue, onInput: (value: string) => emit('update:modelValue', value) }) });
  app.component('el-button', { setup: (_props: unknown, { slots, attrs }: any) => () => vue.h('button', attrs, slots.default?.()) });
  app.component('el-dialog', { props: ['modelValue'], setup: (props: any, { slots }: any) => () => props.modelValue ? vue.h('dialog', slots.default?.()) : null });
  app.mount(root); await vue.nextTick(); await vue.nextTick();
  await click(fixture.title.zh);
}
beforeEach(() => {
  for (const mock of Object.values(api)) mock.mockReset();
  api.listTemplates.mockResolvedValue({ data: { items: [fixture], total: 1, categories: [], feed: { version: 18, online: true } } });
  api.getTemplate.mockResolvedValue({ data: fixture });
  api.prepareTemplate.mockResolvedValue({ data: { model: fixture.model, capability: 'T2I', prompt: 'Minimal poster of 陶瓷花瓶, generous whitespace, editorial typography.', output: fixture.output, referenceAssetIds: [] } });
  const storage = new Map<string, string>();
  vi.stubGlobal('localStorage', { getItem: (key: string) => storage.get(key) || null, setItem: (key: string, value: string) => storage.set(key, value), removeItem: (key: string) => storage.delete(key) });
  vi.stubGlobal('document', { hidden: false });
});
afterEach(() => { app?.unmount(); vi.unstubAllGlobals(); });
describe('image template preview and editable reuse', () => {
  it('shows the sample, full recipe and original parameters without creating a task', async () => {
    await mount();
    expect(nodes().some(n => n.type === 'img' && n.props['data-template'] === fixture.id)).toBe(true);
    expect(nodes().find(n => n.type === 'textarea')?.props.value).toBe(fixture.promptTemplate);
    expect(text(root)).toContain(fixture.model); expect(text(root)).toContain('1024 × 1536'); expect(text(root)).toContain('high');
    expect(api.prepareTemplate).not.toHaveBeenCalled(); expect(api.generateTemplate).not.toHaveBeenCalled(); expect(applied).toEqual([]);
  });
  it.each(['带入编辑', '创作同款'])('%s applies the resolved prompt, exact parameters and display reference without submitting', async label => {
    await mount();
    const input = nodes().find(n => n.type === 'input' && n.props.value === undefined)!;
    input.props.onInput('陶瓷花瓶'); await vue.nextTick();
    expect(nodes().find(n => n.type === 'textarea')?.props.value).toContain('陶瓷花瓶');
    await click(label);
    expect(api.prepareTemplate).toHaveBeenCalledWith({ template_id: fixture.id, revision: 3, variables: { subject: '陶瓷花瓶' } });
    expect(applied).toHaveLength(1); expect(applied[0][0]).toMatchObject({ media: 'image', capability: 'T2I', model: fixture.model, prompt: expect.stringContaining('陶瓷花瓶'), output: fixture.output, templateReference: { id: fixture.id, revision: 3, title: fixture.title.zh } });
    expect(applied[0][0].referenceAssetIds).toBeUndefined(); expect(api.generateTemplate).not.toHaveBeenCalled(); expect(api.prepareVideoTemplate).not.toHaveBeenCalled();
  });
  it('does not emit or submit when required template content fails validation', async () => {
    api.prepareTemplate.mockRejectedValue({ response: { data: { data: { errors: [{ field: 'subject', rule: 'required' }] } } } });
    await mount(); await click('创作同款'); expect(text(root)).toContain('请填写必填内容'); expect(applied).toEqual([]); expect(api.generateTemplate).not.toHaveBeenCalled();
  });
});
