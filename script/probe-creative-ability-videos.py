"""Measure actual Comfy video outputs with ffprobe in an isolated acceptance directory."""
import argparse,json,urllib.request,urllib.parse,subprocess,array,math
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--report',type=Path,required=True);parser.add_argument('--contracts',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--comfy',default='http://192.168.2.223:8188')
args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=True)
op=urllib.request.build_opener(urllib.request.ProxyHandler({}))
contracts=json.loads(args.contracts.read_text(encoding='utf-8'));bindings={b['workflowCode']:b for c in contracts['capabilities'] for b in c['workflows']}
results=[]
for task in json.loads(args.report.read_text(encoding='utf-8')):
 if task['media']!='video' or task['status']!='success':continue
 binding=bindings[task['workflowCode']];expected=binding['supportedOutputs'][0]
 for item in task['outputs']:
  if not item['filename'].endswith('.mp4'):continue
  dest=args.output/item['localFile'];dest.write_bytes(op.open(args.comfy+'/view?'+urllib.parse.urlencode({k:item.get(k,'') for k in ['filename','subfolder','type']}),timeout=60).read())
  measured=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(dest)]))
  stream=next(s for s in measured['streams'] if s['codec_type']=='video');audio=[s for s in measured['streams'] if s['codec_type']=='audio']
  duration=float(stream.get('duration',measured['format']['duration']));a,b=stream['avg_frame_rate'].split('/');fps=int(a)/int(b)
  assert (stream['width'],stream['height'])==(expected['width'],expected['height']),(task['workflowCode'],'size mismatch')
  assert abs(duration-expected['durationSeconds'])<0.15,(task['workflowCode'],'duration mismatch')
  assert abs(fps-expected['fps'])<0.02,(task['workflowCode'],'fps mismatch')
  assert not binding.get('requiresAudio') or audio,(task['workflowCode'],'required audio missing')
  row={'workflowCode':task['workflowCode'],'promptId':task['promptId'],'width':stream['width'],'height':stream['height'],'durationSeconds':duration,'fps':fps,'hasAudio':bool(audio),'audioRequired':bool(binding.get('requiresAudio')),'matchesContract':True}
  if audio:
   raw=subprocess.check_output(['ffmpeg','-v','error','-i',str(dest),'-vn','-ac','1','-ar','16000','-f','f32le','-'])
   samples=array.array('f');samples.frombytes(raw);peak=max(map(abs,samples),default=0)
   row.update(audioCodec=audio[0]['codec_name'],audioPeak=peak,audioRms=math.sqrt(sum(x*x for x in samples)/len(samples)) if samples else 0)
   assert peak>1e-8,(task['workflowCode'],'audio is completely silent')
  results.append(row)
(args.output/'media-probe-report.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'videos_measured':len(results),'required_audio_verified':sum(r['audioRequired'] for r in results),'contract_matches':all(r['matchesContract'] for r in results)}))
