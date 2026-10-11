"""Compile downloaded LiteGraph templates to a native ComfyUI API prompt.
Supports serialized subgraphs, linked widgets, bypassed image inputs and constant switches.
Does not execute or save anything on the server.
"""
import json,sys
from pathlib import Path
OBJ = {}  # Set by the exporter from the target server node schema.
MISSING=object()
SKIP={'Note','MarkdownNote','Reroute','PrimitiveNode'}
class Compiler:
    def __init__(self,w):
        self.w=w;self.subs={s['id']:s for s in w.get('definitions',{}).get('subgraphs',[])};self.prompt={};self.building=set()
        self.root=self.context(w,'',None,None)
    def context(self,g,prefix,parent,instance):
        links={}
        for l in g.get('links',[]):
            if isinstance(l,list):l=dict(zip(['id','origin_id','origin_slot','target_id','target_slot','type'],l))
            links[l['id']]=l
        return {'g':g,'nodes':{n['id']:n for n in g['nodes']},'links':links,'prefix':prefix,'parent':parent,'instance':instance}
    def values(self,n):
        if n.get('widgets_values_named'):return dict(n['widgets_values_named'])
        vals=n.get('widgets_values',[])
        if isinstance(vals,dict):return vals.copy()
        if n['type'] in self.subs:
            if not vals:return {}
            inputs=self.subs[n['type']].get('inputs',[]);keys=[x['name'] for x in inputs if x['type'] in ['INT','FLOAT','BOOLEAN','STRING','COMBO']]
            if len(keys)!=len(vals):raise ValueError('Subgraph widget mapping is ambiguous: '+str(n['id']))
            return dict(zip(keys,vals))
        spec=OBJ.get(n['type'],{});schemas={**spec.get('input',{}).get('required',{}),**spec.get('input',{}).get('optional',{})}
        out={};idx=0
        def consume(fields,prefix=''):
            nonlocal idx
            for key,schema in fields.items():
                t=schema[0];opts=schema[1] if len(schema)>1 else {};name=prefix+key
                if opts.get('hidden'):continue
                widget=isinstance(t,list) or t in ['INT','FLOAT','BOOLEAN','STRING','COMBO','COMFY_DYNAMICCOMBO_V3'] or opts.get('widgetType') in ['INT','FLOAT','BOOLEAN','STRING','COMBO']
                if not widget:continue
                if idx<len(vals):out[name]=vals[idx];idx+=1
                elif 'default' in opts:out[name]=opts['default']
                if opts.get('control_after_generate') and idx<len(vals) and vals[idx] in ['fixed','randomize','increment','decrement']:idx+=1
                if t=='COMFY_DYNAMICCOMBO_V3':
                    option=next((x for x in opts.get('options',[]) if x['key']==out.get(name)),None)
                    if option is None:raise ValueError('Unknown dynamic combo option '+name)
                    inputs=option.get('inputs',{})
                    consume({**inputs.get('required',{}),**inputs.get('optional',{})},name+'.')
        consume(schemas)
        return out
    def inp(self,ctx,n,key):
        i=next((i for i in n.get('inputs',[]) if i['name']==key),None)
        if i and i.get('link') is not None:
            l=ctx['links'][i['link']];val=self.out(ctx,l['origin_id'],l['origin_slot'])
            if val is not MISSING:return val
        return self.values(n).get(key,MISSING)
    def out(self,ctx,nid,slot):
        if nid<0:
            if ctx['parent'] is None:return MISSING
            keys=ctx['g'].get('inputs',[])
            return self.inp(ctx['parent'],ctx['instance'],keys[slot]['name'])
        n=ctx['nodes'][nid];t=n['type'];mode=n.get('mode',0)
        if mode==2:return MISSING
        if mode==4 or t=='Reroute':
            typ=n.get('outputs',[{}])[slot].get('type') if n.get('outputs') else None
            candidates=[i for i in n.get('inputs',[]) if (not typ or i.get('type')==typ) and i.get('link') is not None]
            return self.inp(ctx,n,candidates[0]['name']) if candidates else MISSING
        if t in self.subs:
            inner=self.context(self.subs[t],ctx['prefix']+str(nid)+':',ctx,n)
            links=[l for l in inner['links'].values() if l['target_id']<0 and l['target_slot']==slot]
            if len(links)!=1:raise ValueError('Subgraph output is ambiguous: '+str(nid))
            l=links[0];return self.out(inner,l['origin_id'],l['origin_slot'])
        if t.startswith('Primitive') and t!='PrimitiveNode':return self.inp(ctx,n,'value')
        if t=='ComfySwitchNode':
            val=self.inp(ctx,n,'switch')
            if isinstance(val,bool):return self.inp(ctx,n,'on_true' if val else 'on_false')
        if t=='PrimitiveNode':return (n.get('widgets_values') or [MISSING])[0]
        key=ctx['prefix']+str(nid);self.build(ctx,n)
        return [key,slot]
    def build(self,ctx,n):
        key=ctx['prefix']+str(n['id']);t=n['type']
        if key in self.prompt:return
        if key in self.building:raise ValueError('Cycle at '+key)
        if t not in OBJ:raise ValueError('Unknown executable node '+t)
        self.building.add(key);inputs={}
        for k,v in self.values(n).items():
            if k!='control_after_generate':inputs[k]=v
        for i in n.get('inputs',[]):
            if i.get('link') is not None:
                value=self.inp(ctx,n,i['name'])
                if value is not MISSING:inputs[i['name']]=value
        self.prompt[key]={'class_type':t,'inputs':inputs};self.building.remove(key)
    def compile(self):
        for n in self.w['nodes']:
            if n.get('mode',0)==0 and n['type'] in OBJ and OBJ[n['type']].get('output_node') and n['type'] not in ['PreviewAny','PreviewImage']:
                self.build(self.root,n)
        return self.prompt
