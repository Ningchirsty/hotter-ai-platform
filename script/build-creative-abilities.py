"""Generate business contracts and public schemas from reviewed local workflow sources.

No publishing or remote changes. Run from any directory; generated files use LF.
"""
import copy
import hashlib
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
def read(path): return json.loads(path.read_text(encoding='utf-8'))
def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))
def field(key, label, required=True, options=None, placeholder='', maxLength=250):
    result = dict(key=key, label=label, required=required, maxLength=maxLength, placeholder=placeholder)
    if options: result['options'] = options
    return result
def asset(key, label): return dict(key=key, label=label, required=True)
def ability(code, media, name, desc, cap, sources, inputs, assets, prompt, note, checks):
    return dict(code=code, media=media, name=name, desc=desc, capabilityCode=cap, sources=sources,
                inputs=inputs, assets=assets, promptTemplate=prompt, note=note, acceptance=checks, status='DRAFT')

STYLE = ['商业摄影', '清新简约', '电影质感', '插画风格']
CAMERA = ['缓慢推进', '缓慢拉远', '轻微环绕', '固定镜头']
ABILITIES = [
 ability('POSTER', 'image', '中文海报', '标题、文案与画面一起设计', 'T2I',
 ['wf-local-image-qwen-image-2512', 'wf-local-ready-qwen-image-2-1-t2i', 'wf-local-image-ideogram4-t2i'],
 [field('title','海报标题',placeholder='春日新品'),field('copy','海报文案',False,placeholder='把自然带进生活'),field('visual','画面主体',placeholder='青色陶瓷茶壶与春日花枝'),field('style','设计风格',options=['清新简约','复古印刷','商业摄影','插画风格']),field('layout','版式',options=['标题在上，主体居中','主体在左，文字在右','主体居中，文案在下'])], [],
 '设计一张平面中文海报，直接输出设计稿，不展示墙面、画框或纸张实物。只允许出现两段文字：主标题「${title}」；正文「${copy}」（正文为空时省略）。严格逐字排版，除此之外不要出现任何文字：不要英文、拼音、品牌、数字、说明、标语或乱码。画面主体：${visual}。设计风格：${style}。版式：${layout}。文字清晰、层次分明，画面主体不遮挡文字。',
 '模型直接生成文字与画面，文字和布局需要检查；当前不提供固定字体或文字准确率保证。', ['标题与文案逐字核对','构图与所选版式一致','没有多余文字或水印']),
 ability('QUICK_IMAGE','image','快速配图','为文章和日常内容生成配图','T2I',['wf-local-image-z-image-turbo','wf-local-image-flux2-klein-4b-fast-t2i'],
 [field('subject','画面内容',placeholder='木桌上的咖啡杯与一本打开的书'),field('usage','使用场景',options=['文章配图','社交媒体','活动配图']),field('style','画面风格',options=STYLE)],[],
 '为${usage}制作配图。画面内容：${subject}。风格：${style}。构图清晰，主体突出，不生成文字、水印或额外标志。','适合快速探索画面；输出尺寸以所选工作流为准。',['主题符合输入','画面清晰、没有水印']),
 ability('PRODUCT_SHOT','image','商品场景图','根据商品描述设计摄影场景','T2I',['wf-local-image-krea2-turbo-t2i','wf-local-image-qwen-image-2512'],
 [field('product','商品描述',placeholder='青色陶瓷茶壶，竹编提手'),field('scene','拍摄场景',placeholder='温暖的木桌，窗边自然光'),field('lighting','光线',options=['自然窗光','柔和影棚光','侧面轮廓光'])],[],
 'Commercial product photography. Product: ${product}. Scene: ${scene}. Lighting: ${lighting}. Clear material detail, balanced composition, no text, logos or watermark.','这是描述生成商品图；需要保留已有商品外观时请使用商品换背景。',['商品描述与画面相符','光线与场景一致']),
 ability('BACKGROUND_REPLACE','image','商品换背景','上传商品图片，更换拍摄环境','EDIT',['wf-local-image-flux2-klein-image-edit-4b-distilled','wf-edit-qwen21'],
 [field('background','新背景',placeholder='浅米色影棚背景，柔和自然阴影'),field('lighting','光线',options=['自然窗光','柔和影棚光','侧面轮廓光'])],[asset('image1','商品图片')],
 '只更换输入商品图片 <image1> 的背景。新背景：${background}。光线：${lighting}。尽量保持商品形状、颜色、材质及包装文字，合理匹配阴影，不添加新商品或水印。','指令编辑可能重画商品细节；只需纯白背景时可选择白底商品图，抠图结果仍需对比原图。',['商品形状与包装文字逐项对比','背景符合要求','边缘和阴影自然']),
 ability('STYLE_REDRAW','image','图片风格转换','将原图转换为不同画面风格','I2I',['wf-i2i-qwen21'],
 [field('style','目标风格',options=['水彩插画','油画风格','电影质感','铅笔素描']),field('detail','补充要求',False,placeholder='保留原构图和主体位置')],[asset('img','原始图片')],
 '将输入图片转换成${style}。${detail}。保留主要构图与主体关系，不添加文字或水印。','这是整体重绘，变化程度由重绘幅度控制。',['目标风格明显','主体与构图仍可辨认']),
 ability('CUTOUT','image','透明抠图','去除背景，输出透明 PNG','BGREMOVE',['wf-bgremove-qwen21'],[],[asset('img','待抠图图片')],'','使用固定抠图流程，重点检查主体边缘。',['PNG 含透明通道','主体边缘完整']),
 ability('WHITE_BACKGROUND','image','白底商品图','抠图后由后端合成纯白底','WHITEBG',['wf-whitebg-qwen21'],[],[asset('img','商品图片')],'','由后端把抠图结果合成纯白底，合成步骤保留不透明前景颜色；模型抠图仍可能改变原图细节。',['背景为 RGB 255/255/255','商品边缘完整','白底合成不改写前景颜色']),
 ability('STRUCTURE_GUIDED','image','参考构图出图','沿用参考图轮廓设计新画面','CONTROL',['wf-local-image-z-image-turbo-fun-union-controlnet'],
 [field('subject','新画面内容',placeholder='保持轮廓，生成青色陶瓷茶壶'),field('style','画面风格',options=STYLE)],[asset('img','构图参考图')],
 '根据参考图的轮廓与构图生成：${subject}。风格：${style}。保持整体结构和主体位置，不添加文字或水印。','使用 Canny 与 ControlNet 约束结构，不保证复制原图颜色或纹理。',['轮廓与参考结构一致','内容与风格符合要求']),
 ability('PRODUCT_MOTION','video','商品动效','让商品图片呈现镜头运动','I2V',['wf-local-ready-wan22-a14b-4steps-i2v','wf-local-video-hunyuan-video-1.5-720p-i2v','wf-local-video-kandinsky5-i2v'],
 [field('action','动态要求',placeholder='茶壶冒出轻柔蒸汽，商品稳定'),field('camera','镜头运动',options=CAMERA)],[asset('img','商品图片')],
 'Commercial product motion shot based on the provided image. Action: ${action}. Camera: ${camera}. Keep the product shape and scene coherent. No subtitles, logos or watermark.','先使用已验证的短片档位；此用途不要求音轨。',['主体稳定，没有明显变形','动作和镜头符合要求']),
 ability('SOUND_STORY','video','有声短片','文字描述生成画面与声音','T2V',['wf-local-video-ltx2-5-t2v','wf-local-ready-minimaxh3-turbo-t2v-av'],
 [field('scene','场景与动作',placeholder='陶瓷茶壶放在木桌上，蒸汽缓缓升起'),field('sound','声音方向',placeholder='安静室内氛围，轻柔沸水声'),field('camera','镜头运动',options=CAMERA)],[],
 'Scene and action: ${scene}. Camera: ${camera}. Audio: ${sound}. Continuous coherent short shot. No subtitles, logos or watermark.','要求成片含音轨；声音内容与画面是否匹配仍需试听。',['成片存在音轨','声音与画面匹配','动作连续']),
 ability('PHOTO_ANIMATION','video','照片动起来','为照片添加自然动作','I2V',['wf-local-ready-wan22-5b-i2v','wf-local-video-kandinsky5-i2v','wf-local-video-ltx2-5-i2v'],
 [field('action','照片中的动作',placeholder='花枝随微风轻轻摇动'),field('camera','镜头运动',options=CAMERA)],[asset('img','原始照片')],
 'Animate the provided photograph. Action: ${action}. Camera: ${camera}. Keep the scene and subject consistent with the starting image, natural motion, no subtitles or watermark.','输入照片作为起始参考，避免过大的动作导致主体变形。',['首帧与参考照片相符','主体与场景连续']),
 ability('SCENE_CLIP','video','场景短片','根据描述生成一个连续镜头','T2V',['wf-local-ready-wan22-5b-t2v','wf-local-video-hunyuan-video-1.5-720p-t2v','wf-local-video-kandinsky5-t2v'],
 [field('scene','场景与主体',placeholder='清晨森林，阳光穿过树叶'),field('action','场景变化',placeholder='薄雾缓缓飘动'),field('camera','镜头运动',options=CAMERA)],[],
 'Scene: ${scene}. Motion: ${action}. Camera: ${camera}. A single continuous short shot, coherent lighting and natural movement. No text or watermark.','单镜头短片，不承诺多镜头剪辑或长片生成。',['场景与输入相符','运动连续，没有明显闪烁']),
 ability('KEYFRAME_TRANSITION','video','首尾画面过渡','连接指定的开始与结束画面','FL2V',['wf-local-ready-wan22-a14b-4steps-flf2v','wf-local-video-ltx2-5-flf2v','wf-local-ready-minimaxh3-turbo-flf2v-av'],
 [field('transition','过渡要求',placeholder='从商品特写平滑拉远到完整场景')],[asset('first','开始画面'),asset('last','结束画面')],
 'Use the provided start image as the first frame and end image as the final frame anchor. Transition: ${transition}. Smooth coherent motion, no black frames, text or watermark.','首尾图是约束参考；需检查实际首尾帧和中间变化。',['起始与结束画面符合参考','中间过渡自然']),
 ability('REFERENCE_STORY','video','双图参考短片','结合两张参考图生成音画短片','R2V',['wf-local-video-minimax-h3-r2v'],
 [field('story','两张图片如何出现在短片中',placeholder='<Picture 1> 中的茶壶放到 <Picture 2> 的桌面环境'),field('sound','声音方向',placeholder='轻柔室内氛围声')],[asset('reference1','主体参考图'),asset('reference2','场景参考图')],
 'Use <Picture 1> and <Picture 2> as visual references. Story: ${story}. Audio: ${sound}. Coherent short shot, no subtitles or watermark.','双参考不等同于首尾帧，也不提供人物动作迁移。',['两张参考图的指定内容可辨认','存在音轨','场景连贯'])
]

manifest = read(ROOT/'script/local-workflow-import-20261010.json')
catalogs = {m: read(ROOT/f'frontend/src/views/{m}/local-workflows.json') for m in ['image','video']}
native = {w['workflowCode']: w for w in manifest}
public_native = {w['workflowCode']: w for rows in catalogs.values() for w in rows}
legacy = {}
for media in ['image','video']:
    for cap in read(ROOT/f'script/{media}/workflows/{media}-workflow-contracts.json')['capabilities']:
        for binding in cap['workflows']:
            legacy[binding['workflowCode']] = (cap, binding)

def mapping_for(row, graph):
    """Resolve the positive prompt scalar; negative prompt nodes stay immutable."""
    maps = []
    positive = [(key,node) for key,node in graph.items() if node['class_type'] in ['CLIPTextEncode','TextEncodeQwenImage21','MiniMaxH3ImageToVideo','MiniMaxH3ReferenceToVideo']]
    # Positive is the first encoder in the reviewed API sources (verified against sampler links in tests).
    if row['modelCode'] == 'KREA':
        key,node = next((k,n) for k,n in graph.items() if n['class_type']=='TextGenerate')
        prompt_key = 'prompt'
    else:
        key,node = positive[0]
        prompt_key = 'prompt' if 'prompt' in node['inputs'] else 'text'
    value = node['inputs'][prompt_key]
    if isinstance(value, list):
        key = str(value[0]); node = graph[key]
        scalar = [('string_b', node['inputs']['string_b'])] if node['class_type']=='StringConcatenate' else [(k,v) for k,v in node['inputs'].items() if isinstance(v,str)]
        assert len(scalar)==1, (row['workflowCode'],node)
        prompt_key = scalar[0][0]
    maps.append(dict(field='prompt' if row['media']=='image' else 'desc',nodeId=key,inputKey=prompt_key))
    images = [(k,n) for k,n in graph.items() if n['class_type']=='LoadImage']
    slots = {'EDIT':['image1'],'CONTROL':['img'],'I2V':['img'],'FL2V':['first','last'],'R2V':['reference1','reference2']}.get(row['capabilityCode'],[])
    assert len(images)==len(slots), (row['workflowCode'],images,slots)
    for slot,(key,_) in zip(slots, images): maps.append(dict(field=slot,nodeId=key,inputKey='image'))
    for key,node in graph.items():
        for seed in ['seed','noise_seed','sampling_mode.seed']:
            if seed in node['inputs'] and isinstance(node['inputs'][seed],int): maps.append(dict(field='_seed',nodeId=key,inputKey=seed))
    assert any(m['field']=='_seed' for m in maps)
    return maps

contracts = {'image':[], 'video':[]}
all_public = {'image':[], 'video':[]}
def native_binding(row, code=None, ability_name=None):
    media=row['media']; code=code or row['workflowCode']
    graph=read(ROOT/'script'/row['apiJsonFile']); source=public_native[row['workflowCode']]
    maps=mapping_for(row,graph)
    path=row['apiJsonFile']
    if ability_name:
        for n in graph.values():
            if 'filename_prefix' in n['inputs']: n['inputs']['filename_prefix']='Codex_Abilities/'+code
        path=f'{media}/workflows/abilities-api/{code}.json'
        write(ROOT/'script'/path,graph)
    checksum=hashlib.sha256((ROOT/'script'/path).read_bytes()).hexdigest()
    output=row['output']; out_node=next(k for k,n in graph.items() if n['class_type'] in ['SaveVideo','SaveImage','SaveImageAdvanced'])
    binding=dict(workflowCode=code,modelCode=row['modelCode'],version='v1.0.0-ability' if ability_name else source['version'],status='DRAFT',apiJsonFile=path,checksum=checksum,mapping=maps,
                 outputRule=dict(nodeId=out_node,outputField='images' if media=='image' else 'videos',format='png' if media=='image' else 'mp4',mime='image/png' if media=='image' else 'video/mp4',maxPixels=max(4194304,output['width']*output['height']),maxSizeMB=128),
                 supportedOutputs=[output],requiresAudio=bool(source.get('hasAudio')),perf=dict(timeoutSeconds=1800),fixedFieldValidation={})
    if media=='image':
        binding['supportedOutputs']=[dict(size=source['sizes'][0]['label'],width=output['width'],height=output['height'])]
        if row['capabilityCode']=='T2I': binding['fixedFieldValidation']=dict(size=source['sizes'][0]['label'],supportedSizes=[source['sizes'][0]['label']])
    else:
        tier=source['supportedTiers'][0]; dur=source['supportedDuration']
        binding['fixedFieldValidation']=dict(tier=tier,dur=dur,supportedTiers=[tier])
        binding['outputRule']['maxDurationSeconds']=math.ceil(output['durationSeconds']+1)
    public=copy.deepcopy(source);public.update(workflowCode=code,version=binding['version'],apiChecksum=checksum)
    if ability_name: public.update(name=ability_name+' · 专用流程', verifiedAt=None)
    cap=dict(capabilityCode=row['capabilityCode'],fields=[dict(field=f) for f in source['fields']],workflows=[binding])
    contracts[media].append(cap); all_public[media].append(public)
    return public

for row in manifest: native_binding(row)
for definition in ABILITIES:
    media=definition['media']; choices=[]
    for source_code in definition.pop('sources'):
        model = native[source_code]['modelCode'] if source_code in native else legacy[source_code][1]['modelCode']
        alias=f"wf-ability-{media}-{definition['code'].lower().replace('_','-')}-{model.lower()}"
        if source_code in native:
            public=native_binding(native[source_code],alias,definition['name'])
        else:
            original_cap,original=legacy[source_code]; binding=copy.deepcopy(original)
            path=f'{media}/workflows/abilities-api/{alias}.json'
            graph=read(ROOT/'script'/original['apiJsonFile'])
            for node in graph.values():
                if 'filename_prefix' in node['inputs']: node['inputs']['filename_prefix']='Codex_Abilities/'+alias
            write(ROOT/'script'/path,graph)
            binding.update(workflowCode=alias,status='DRAFT',version='v1.0.0-ability',apiJsonFile=path,checksum=hashlib.sha256((ROOT/'script'/path).read_bytes()).hexdigest())
            contracts[media].append(dict(capabilityCode=original_cap['capabilityCode'],fields=original_cap['fields'],workflows=[binding]))
            public=dict(workflowCode=alias,media=media,capabilityCode=original_cap['capabilityCode'],modelCode=model,modelName='Qwen-Image 2.1',name=definition['name']+' · 专用流程',version=binding['version'],apiChecksum=binding['checksum'],fields=[f['field'] for f in original_cap['fields']],
                        sizes=[dict(label=o['size'],width=o['width'],height=o['height']) for o in binding.get('supportedOutputs',[]) if o['width']>0],defaultSize=binding.get('fixedFieldValidation',{}).get('size'),strengths=binding.get('fixedFieldValidation',{}).get('supportedStrengths',[]))
            all_public[media].append(public)
        public['sourceWorkflowCode']=source_code
        public['abilityCode']=definition['code']
        public.pop('description',None)
        choices.append(public)
    definition['workflows']=choices
    definition['recommendedWorkflowCode']=choices[0]['workflowCode']

for media in ['image','video']:
    defs=[d for d in ABILITIES if d['media']==media]
    write(ROOT/f'script/{media}/workflows/abilities.json',defs)
    write(ROOT/f'script/{media}/workflows/native-workflow-contracts.json',dict(capabilities=contracts[media]))
    write(ROOT/f'frontend/src/views/{media}/abilities.json',defs)
print(json.dumps(dict(abilities={m:sum(d['media']==m for d in ABILITIES) for m in ['image','video']},native_and_business_bindings={m:len(v) for m,v in contracts.items()})))
