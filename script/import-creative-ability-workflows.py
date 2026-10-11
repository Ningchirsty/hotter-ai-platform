"""Import dedicated canvas workflows without replacing existing user workflows."""
import argparse,json,hashlib,urllib.request,urllib.parse,urllib.error
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--canvas',type=Path,required=True);parser.add_argument('--report',type=Path,required=True);parser.add_argument('--comfy',default='http://192.168.2.223:8188');parser.add_argument('--replace-previous-quick-api',type=Path)
args=parser.parse_args();op=urllib.request.build_opener(urllib.request.ProxyHandler({}));records=[]
models={'qwen2512':'Qwen2512','qwen21':'Qwen21','zimage':'ZImage','flux4b':'FluxKlein4B','ideogram':'Ideogram4','krea':'Krea2','wan':'Wan22','ltx':'LTX25','hun':'Hunyuan15','h3':'MiniMaxH3','kand':'Kandinsky5'}
for item in json.loads((args.canvas/'manifest.json').read_text(encoding='utf-8')):
 code=item['workflowCode'];body=(args.canvas/item['file']).read_bytes();model=models[code.rsplit('-',1)[-1]]
 remote='Codex-Abilities/'+('图像' if item['media']=='image' else '视频')+'/'+item['name']+'_'+model+'.json'
 route=args.comfy+'/api/userdata/'+urllib.parse.quote('workflows/'+remote,safe='')
 try:
  response=op.open(urllib.request.Request(route+'?overwrite=false',data=body,headers={'Content-Type':'application/json'},method='POST'),timeout=30);status=response.status
 except urllib.error.HTTPError as error:
  if error.code!=409:raise
  existing=op.open(route,timeout=30).read()
  if existing!=body:
   if not (code=='wf-ability-image-quick-image-zimage' and args.replace_previous_quick_api and existing==args.replace_previous_quick_api.read_bytes()):
    raise RuntimeError('Existing workflow differs; refusing to replace '+remote)
   response=op.open(urllib.request.Request(route+'?overwrite=true',data=body,headers={'Content-Type':'application/json'},method='POST'),timeout=30);status=response.status
  else:status=409
 returned=op.open(route,timeout=30).read()
 if returned!=body:raise RuntimeError('Imported workflow readback differs: '+remote)
 records.append({**item,'remote':remote,'httpStatus':status,'readbackMatches':True})
 print('IMPORTED',code,flush=True)
args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
print('CANVAS_IMPORTED_AND_READBACK_VERIFIED='+str(len(records)))
