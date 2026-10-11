"""Export canvas workflows from real backend plans and verify their executable inputs."""
import argparse,json,uuid,hashlib
from pathlib import Path
import creative_workflow

parser=argparse.ArgumentParser()
parser.add_argument('--plans',type=Path,required=True)
parser.add_argument('--object-info',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
obj=json.loads(args.object_info.read_text(encoding='utf-8'));creative_workflow.OBJ=obj
plans=json.loads((args.plans/'plans.json').read_text(encoding='utf-8'))
args.output.mkdir(parents=True,exist_ok=True)
records=[]
# Constants that were inlined by the backend compiler must be restored as visible input nodes.
WIDGET={'INT','FLOAT','BOOLEAN','STRING','COMBO','COMFY_DYNAMICCOMBO_V3'}
for plan in plans:
 code=plan['workflowCode'];graph=json.loads((args.plans/(code+'.json')).read_text(encoding='utf-8'))
 node_ids={key:i+1 for i,key in enumerate(graph)};nodes=[];links=[];depths={};widget_keys={}
 def depth(key,seen=None):
  if key in depths:return depths[key]
  seen=set() if seen is None else seen
  if key in seen:raise ValueError('cycle in prepared graph')
  parents=[v[0] for v in graph[key]['inputs'].values() if isinstance(v,list) and len(v)==2 and v[0] in graph]
  value=0 if not parents else max(depth(p,seen|{key}) for p in parents)+1
  depths[key]=value;return value
 rows={}
 for key,value in graph.items():
  info=obj[value['class_type']];scalars=value['inputs'];widgets=[];named={}
  def consume(schemas,prefix=''):
   for name,spec in schemas.items():
    name=prefix+name;kind=spec[0];options=spec[1] if len(spec)>1 else {}
    if options.get('hidden'):continue
    if not (isinstance(kind,list) or isinstance(kind,str) and kind in WIDGET or options.get('widgetType') in WIDGET):continue
    current=scalars.get(name, scalars.get('codec') if value['class_type']=='SaveVideo' and name=='format.codec' else None)
    if isinstance(current,list):current=options.get('default',kind[0] if isinstance(kind,list) and kind else '')
    if current is None:current=options.get('default',options.get('options',[{}])[0].get('key','') if kind=='COMFY_DYNAMICCOMBO_V3' else kind[0] if isinstance(kind,list) and kind else 0 if kind in ['INT','FLOAT'] else False if kind=='BOOLEAN' else '')
    widgets.append(current);named[name]=current
    if options.get('control_after_generate'):widgets.append('fixed')
    if kind=='COMFY_DYNAMICCOMBO_V3':
     selected=next(x for x in options['options'] if x['key']==current)
     fields=selected.get('inputs',{});consume({**fields.get('required',{}),**fields.get('optional',{})},name+'.')
  consume({**info['input'].get('required',{}),**info['input'].get('optional',{})})
  column=depth(key);row=rows.get(column,0);rows[column]=row+1
  node=dict(id=node_ids[key],type=value['class_type'],title=value.get('_meta',{}).get('title',info.get('display_name',value['class_type'])),pos=[column*410,row*450],size=[350,380],flags={},order=len(nodes),mode=0,
    inputs=[],outputs=[dict(name=n,type=t,links=[]) for n,t in zip(info.get('output_name',info['output']),info['output'])],properties={'Node name for S&R':value['class_type']},widgets_values=widgets)
  nodes.append(node);widget_keys[key]=set(named)
 by_id={n['id']:n for n in nodes}
 for key,value in graph.items():
  target=by_id[node_ids[key]]
  schemas={**obj[value['class_type']]['input'].get('required',{}),**obj[value['class_type']]['input'].get('optional',{})}
  for name,ref in value['inputs'].items():
   if not (isinstance(ref,list) and len(ref)==2 and ref[0] in graph):continue
   source=by_id[node_ids[ref[0]]];output=source['outputs'][ref[1]];link_id=len(links)+1;slot=len(target['inputs'])
   inp=dict(name=name,type=output['type'],link=link_id)
   spec=schemas.get(name)
   if spec and (isinstance(spec[0],list) or spec[0] in WIDGET):inp['widget']={'name':name}
   target['inputs'].append(inp);output['links'].append(link_id)
   links.append([link_id,source['id'],ref[1],target['id'],slot,output['type']])
 for key,value in graph.items():
  target=by_id[node_ids[key]]
  for name,scalar in value['inputs'].items():
   if isinstance(scalar,list) or name in widget_keys[key] or value['class_type']=='SaveVideo' and name=='codec':continue
   kind='PrimitiveBoolean' if isinstance(scalar,bool) else 'PrimitiveInt' if isinstance(scalar,int) else 'PrimitiveFloat' if isinstance(scalar,float) else 'PrimitiveString' if isinstance(scalar,str) else None
   assert kind,(code,key,name,'unsupported scalar')
   info=obj[kind];new_id=len(nodes)+1;port=info['output'][0]
   widgets=[scalar]
   if info['input']['required']['value'][1].get('control_after_generate'):widgets.append('fixed')
   source=dict(id=new_id,type=kind,title=name,pos=[target['pos'][0]-390,target['pos'][1]+len(target['inputs'])*100],size=[300,90],flags={},order=len(nodes),mode=0,inputs=[],outputs=[dict(name=info['output_name'][0],type=port,links=[])],properties={'Node name for S&R':kind},widgets_values=widgets)
   nodes.append(source);link_id=len(links)+1;slot=len(target['inputs'])
   target['inputs'].append(dict(name=name,type=port,link=link_id));source['outputs'][0]['links'].append(link_id)
   links.append([link_id,new_id,0,target['id'],slot,port])
 workflow=dict(id=str(uuid.uuid5(uuid.NAMESPACE_URL,code)),revision=0,last_node_id=len(nodes)+1,last_link_id=len(links),nodes=nodes,links=links,groups=[],config={},extra={'codex_ability':{'code':plan['abilityCode'],'workflowCode':code,'media':plan['media'],'backendPreparedGraphSha256':hashlib.sha256((args.plans/(code+'.json')).read_bytes()).hexdigest(),'publicationStatus':'DRAFT'}},version=0.4)
 check=creative_workflow.Compiler(workflow).compile()
 for key,value in graph.items():
  actual=check[str(node_ids[key])]
  assert actual['class_type']==value['class_type']
  for name,expected in value['inputs'].items():
   if isinstance(expected,list) and len(expected)==2 and expected[0] in node_ids:expected=[str(node_ids[expected[0]]),expected[1]]
   if value['class_type']=='SaveVideo' and name=='codec' and actual['inputs'].get('format.codec')==expected:continue
   assert actual['inputs'].get(name)==expected,(code,key,name,actual['inputs'].get(name),expected)
 note=plan['name']+' · '+code+'\n输入与描述由平台用途表单校验，再由后端填入此节点图。\n当前保存的是独立实测样例，可手动调整文字和素材。\n后端发布状态：DRAFT。'
 if plan['abilityCode']=='WHITE_BACKGROUND':note+='\n此图输出透明 PNG 中间产物，平台后端 ImageWhiteBackgroundCompositor 再合成纯白底。'
 workflow['nodes'].append(dict(id=len(nodes)+1,type='Note',title=plan['name']+' · 用途契约',pos=[-500,0],size=[460,260],flags={},order=0,mode=0,inputs=[],outputs=[],properties={},widgets_values=[note]))
 path=args.output/(code+'.json');data=(json.dumps(workflow,ensure_ascii=False,indent=2)+'\n').encode();path.write_bytes(data)
 records.append({'workflowCode':code,'abilityCode':plan['abilityCode'],'media':plan['media'],'name':plan['name'],'file':path.name,'sha256':hashlib.sha256(data).hexdigest(),'preparedInputsMatch':True})
(args.output/'manifest.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
print('CANVAS_WORKFLOWS_EXPORTED_AND_INPUTS_VERIFIED='+str(len(records)))
