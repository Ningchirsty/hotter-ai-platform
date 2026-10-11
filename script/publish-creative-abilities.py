"""Publish purpose bindings only from complete platform task evidence and unchanged source contracts."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess

from creative_publication import schema_checksum

ROOT = Path(__file__).resolve().parents[1]

def read(file):
    return json.loads(file.read_text(encoding='utf8'))

def validate_video_output(probe, supported, requires_audio):
    video = next(stream for stream in probe['streams'] if stream['codec_type']=='video')
    numerator,denominator = map(float,video['avg_frame_rate'].split('/'))
    fps = numerator/denominator
    duration = float(probe['format']['duration'])
    matched = any((video['width'],video['height']) == (spec['width'],spec['height'])
                  and abs(fps-spec['fps']) < 0.01
                  and abs(duration-spec['durationSeconds']) <= 1/spec['fps']+0.01
                  for spec in supported)
    if not matched:
        raise ValueError('Stored video dimensions, frame rate or duration do not match its registered output')
    if requires_audio and not any(stream['codec_type']=='audio' for stream in probe['streams']):
        raise ValueError('Stored media is missing required audio')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence',type=Path,required=True)
    parser.add_argument('--run-id',required=True)
    parser.add_argument('--ffprobe',type=Path,required=True,help='Verified ffprobe executable for stored video validation')
    args = parser.parse_args()
    report = read(args.evidence)
    if report.get('scope') != 'isolated-platform' or report.get('technicalAcceptance') != 'PASSED' or report.get('productionModified') is not False:
        raise ValueError('Complete isolated platform acceptance is required')
    tested_commit = report.get('commit','')
    if not re.fullmatch(r'[0-9a-f]{40}',tested_commit) or not re.fullmatch(r'[1-9][0-9]*',args.run_id):
        raise ValueError('Invalid immutable source commit or Actions run ID')
    rows = {row['workflowCode']:row for row in report['workflows']}
    if len(rows) != len(report['workflows']):
        raise ValueError('Duplicate acceptance rows')
    expected = {}
    native = {}
    for media in ['image','video']:
        contract = read(ROOT/f'script/{media}/workflows/native-workflow-contracts.json')
        native.update({b['workflowCode']:b for cap in contract['capabilities'] for b in cap['workflows']})
        path = f'script/{media}/workflows/abilities.json'
        source = json.loads(subprocess.check_output(['git','show',tested_commit+':'+path],cwd=ROOT))
        definitions = read(ROOT/path)
        before = {b['workflowCode']:schema_checksum(a,b) for a in source for b in a['workflows']}
        for ability in definitions:
            for binding in ability['workflows']:
                code = binding['workflowCode']
                if before.get(code) != schema_checksum(ability,binding):
                    raise ValueError('Purpose or output controls changed after validation: '+code)
                expected[code] = (ability,binding)
    if set(expected) != set(rows) or len(rows) != 28:
        raise ValueError('Evidence must cover all 13 image and 15 video bindings')
    verified_at = datetime.now(timezone.utc).isoformat()
    entries = []
    for code,(ability,binding) in expected.items():
        row = rows[code]
        if row.get('status') != 'SUCCEEDED' or row.get('media') != ability['media'] or row.get('abilityCode') != ability['code']:
            raise ValueError('Task did not validate this purpose: '+code)
        if not row.get('taskId') or not row.get('outputAssetId') or not row.get('comfyPromptId'):
            raise ValueError('Platform/Comfy/storage trace is incomplete: '+code)
        name = code + ('.png' if ability['media']=='image' else '.mp4')
        output = args.evidence.parent / name
        if row.get('output') != name or not output.is_file():
            raise ValueError('Generated media evidence is missing: '+code)
        if output.stat().st_size != row.get('bytes') or hashlib.sha256(output.read_bytes()).hexdigest() != row.get('sha256'):
            raise ValueError('Generated media checksum does not match: '+code)
        graph = ROOT / 'script' / ability['media'] / 'workflows/abilities-api' / (code+'.json')
        if hashlib.sha256(graph.read_bytes()).hexdigest() != binding['apiChecksum']:
            raise ValueError('API graph changed after validation: '+code)
        if binding.get('hasAudio') and not row.get('hasAudio'):
            raise ValueError('Required audio was not verified: '+code)
        if ability['media'] == 'video':
            probe = json.loads(subprocess.check_output([str(args.ffprobe),'-v','error','-show_streams','-show_format','-of','json',str(output)]))
            validate_video_output(probe,native[code]['supportedOutputs'],binding.get('hasAudio',False))
        else:
            from PIL import Image
            with Image.open(output) as image:
                selected_size = row.get('outputOptions',{}).get('size')
                if selected_size:
                    size = next(spec for spec in binding['sizes'] if spec['label']==selected_size)
                    if image.size != (size['width'],size['height']):
                        raise ValueError('Stored image does not match its selected size: '+code)
                if ability['code']=='CUTOUT' and ('A' not in image.getbands() or image.getchannel('A').histogram()[0] < image.width*image.height*0.005):
                    raise ValueError('Stored cutout has no useful transparent area: '+code)
                if ability['code']=='WHITE_BACKGROUND':
                    white = sum(1 for pixel in image.convert('RGB').getdata() if pixel == (255,255,255))
                    if image.mode != 'RGB' or white < image.width*image.height*0.005:
                        raise ValueError('Stored white background output is not opaque with useful pure-white area: '+code)
        entries.append(dict(workflowCode=code,media=ability['media'],apiChecksum=binding['apiChecksum'],
                            schemaChecksum=schema_checksum(ability,binding),testedCommit=tested_commit,
                            technicalAcceptance='PASSED',qualityReview=report.get('qualityReview','pending'),
                            pipelineUrl='https://github.com/Ningchirsty/hotter-ai-platform/actions/runs/'+args.run_id,
                            taskId=row['taskId'],comfyPromptId=row['comfyPromptId'],outputSha256=row['sha256'],verifiedAt=verified_at))
    destination = ROOT/'script/creative-ability-publication.json'
    destination.write_bytes((json.dumps(dict(workflows=entries),ensure_ascii=False,indent=2)+'\n').encode())
    subprocess.run([__import__('sys').executable,str(ROOT/'script/build-creative-abilities.py')],cwd=ROOT,check=True)
    print('TECHNICAL_PUBLICATION_PREPARED',len(entries),'qualityReview='+report.get('qualityReview','pending'))

if __name__ == '__main__':
    main()
