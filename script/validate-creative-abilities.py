"""Execute backend-compiled ability graphs against ComfyUI; never publishes bindings."""
import argparse,json,time,hashlib,urllib.request,urllib.parse,urllib.error
from pathlib import Path
from PIL import Image
import cv2

parser=argparse.ArgumentParser()
parser.add_argument('--plans',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--comfy',default='http://192.168.2.223:8188')
parser.add_argument('--recommended-only',action='store_true')
args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=True)
op=urllib.request.build_opener(urllib.request.ProxyHandler({}))
def get(route):return op.open(args.comfy+route,timeout=40).read()
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
report_path=args.output/'execution-report.json'
report=json.loads(report_path.read_text(encoding='utf-8')) if report_path.exists() else []
plans=json.loads((args.plans/'plans.json').read_text(encoding='utf-8'))
plans=sorted(plans,key=lambda p:not p['recommended'])
for plan in plans:
 if args.recommended_only and not plan['recommended']:continue
 code=plan['workflowCode']
 graph_bytes=(args.plans/(code+'.json')).read_bytes();graph=json.loads(graph_bytes)
 graph_sha=hashlib.sha256(graph_bytes).hexdigest()
 if any(r['workflowCode']==code and r['status']=='success' and r.get('graphSha256')==graph_sha for r in report):continue
 request={'prompt':graph,'client_id':'codex-creative-ability-acceptance'}
 row={k:plan[k] for k in ['workflowCode','abilityCode','media','name','recommended','request','normalized','hasAudio']}
 row['graphSha256']=graph_sha
 try:
  submitted=json.load(op.open(urllib.request.Request(args.comfy+'/prompt',data=json.dumps(request).encode(),headers={'Content-Type':'application/json'},method='POST'),timeout=40))
  row['promptId']=submitted['prompt_id'];row['status']='running';report.append(row);write(report_path,report)
  print('SUBMITTED',code,row['promptId'],flush=True)
  started=time.monotonic();last_print=started
  while True:
   history=json.loads(get('/history/'+row['promptId']))
   if row['promptId'] in history:break
   if time.monotonic()-started>1800:raise TimeoutError('Comfy execution exceeded 1800 seconds; its prompt ID remains recorded')
   if time.monotonic()-last_print>30:print('RUNNING',code,round(time.monotonic()-started),flush=True);last_print=time.monotonic()
   time.sleep(3)
  item=history[row['promptId']];write(args.output/(code+'-history.json'),item)
  row['seconds']=round(time.monotonic()-started,2);row['comfyStatus']=item['status']
  if item['status']['status_str']!='success':raise RuntimeError('Comfy execution failed; see recorded history')
  row['outputs']=[]
  for node_id,output in item.get('outputs',{}).items():
   for kind in ['images','videos','gifs']:
    for file in output.get(kind,[]):
     if not file.get('filename'):continue
     body=get('/view?'+urllib.parse.urlencode({k:file.get(k,'') for k in ['filename','subfolder','type']}))
     destination=args.output/(code+'-'+str(node_id)+Path(file['filename']).suffix)
     destination.write_bytes(body)
     meta={**file,'nodeId':node_id,'localFile':destination.name,'sha256':hashlib.sha256(body).hexdigest(),'bytes':len(body)}
     if destination.suffix.lower()=='.png':
      with Image.open(destination) as image:
       meta.update(width=image.width,height=image.height,mode=image.mode)
       if 'A' in image.getbands():meta['transparentRatio']=image.getchannel('A').histogram()[0]/(image.width*image.height)
     elif destination.suffix.lower()=='.mp4':
      capture=cv2.VideoCapture(str(destination));frames=int(capture.get(cv2.CAP_PROP_FRAME_COUNT));fps=capture.get(cv2.CAP_PROP_FPS)
      meta.update(width=int(capture.get(cv2.CAP_PROP_FRAME_WIDTH)),height=int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT)),frames=frames,fps=fps,durationSeconds=frames/fps if fps else None)
      for tag,frame in [('first',0),('middle',frames//2),('last',frames-1)]:
       capture.set(cv2.CAP_PROP_POS_FRAMES,frame);ok,bgr=capture.read()
       if ok:cv2.imwrite(str(args.output/(code+'-'+tag+'.png')),bgr)
      capture.release()
     row['outputs'].append(meta)
  if not row['outputs']:raise RuntimeError('No downloadable output returned')
  if row['abilityCode']=='CUTOUT' and not any(o.get('transparentRatio',0)>0.005 for o in row['outputs']):raise RuntimeError('Cutout output does not contain a useful alpha mask')
  row['status']='success';print('SUCCESS',code,row['seconds'],flush=True)
 except urllib.error.HTTPError as error:
  row['status']='failed';row['error']=error.read().decode('utf-8','replace')
  if row not in report:report.append(row)
  print('FAILED',code,row['error'][:500],flush=True)
 except Exception as error:
  row['status']='failed';row['error']=str(error)
  if row not in report:report.append(row)
  print('FAILED',code,str(error),flush=True)
 write(report_path,report)
print('RESULTS',json.dumps({s:sum(r['status']==s for r in report) for s in ['success','failed','running']}),flush=True)

latest={row['workflowCode']:row for row in report}
if any(row['status']!='success' for row in latest.values()):raise SystemExit(1)
