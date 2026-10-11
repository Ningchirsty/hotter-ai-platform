"""Export/load immutable OCI blobs without forwarding registry credentials to the CDN."""
import argparse
import base64
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

REPOSITORIES = {'ningchirsty/hotter-ai-platform-backend', 'ningchirsty/hotter-ai-platform-frontend'}
MAX_IMAGE = 2 * 1024**3
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url): return None

def reference_parts(reference):
    match = re.fullmatch(r'ghcr\.io/([^@]+)@(sha256:[0-9a-f]{64})', reference)
    if not match or match[1] not in REPOSITORIES: raise ValueError('Expected an immutable production image reference')
    return match[1], match[2]

def checked_content(stream, limit=MAX_IMAGE):
    value = hashlib.sha256();size=0
    for chunk in iter(lambda: stream.read(1024**2), b''):
        size+=len(chunk)
        if size>limit: raise ValueError('Uncompressed image content size bound exceeded')
        value.update(chunk)
    return 'sha256:' + value.hexdigest(),size

def checksum(stream, limit=MAX_IMAGE): return checked_content(stream,limit)[0]

def validate_config(config, revision, layer_count):
    if not re.fullmatch(r'[0-9a-f]{40}', revision): raise ValueError('Expected full tested Git SHA')
    if config.get('config', {}).get('Labels', {}).get('org.opencontainers.image.revision') != revision:
        raise ValueError('Image revision differs from the tested commit')
    if config.get('architecture') != 'amd64' or config.get('os') != 'linux': raise ValueError('Unexpected image platform')
    ids = config.get('rootfs', {}).get('diff_ids', [])
    if len(ids) != layer_count or any(not re.fullmatch(r'sha256:[0-9a-f]{64}', value) for value in ids):
        raise ValueError('Invalid image root filesystem descriptors')
    return ids

def descriptor(value):
    if not re.fullmatch(r'sha256:[0-9a-f]{64}', value.get('digest', '')) or not isinstance(value.get('size'), int) or not 0 <= value['size'] <= MAX_IMAGE:
        raise ValueError('Invalid bounded content descriptor')
    return 'blobs/sha256/' + value['digest'].split(':')[1]

def manifest_data(raw, digest):
    if 'sha256:' + hashlib.sha256(raw).hexdigest() != digest: raise ValueError('Immutable manifest checksum mismatch')
    value = json.loads(raw)
    if value.get('schemaVersion') != 2 or not 1 <= len(value.get('layers', [])) <= 32:
        raise ValueError('Expected the single-platform image published by CI')
    if sum(d['size'] for d in [value['config'], *value['layers']]) > MAX_IMAGE: raise ValueError('Image content size bound exceeded')
    return value

def export(reference, revision, destination):
    repository, digest = reference_parts(reference)
    registry = urllib.request.build_opener(NoRedirect)
    delivery = urllib.request.build_opener(NoRedirect)
    def metadata(url, headers=None):
        with registry.open(urllib.request.Request(url, headers=headers or {}), timeout=45) as response:
            data = response.read(4*1024**2 + 1)
            if len(data) > 4*1024**2: raise ValueError('Registry metadata size bound exceeded')
            return data
    credentials = base64.b64encode((os.environ['GITHUB_ACTOR']+':'+os.environ['GHCR_TOKEN']).encode()).decode()
    token_url = 'https://ghcr.io/token?' + urllib.parse.urlencode({'service':'ghcr.io','scope':'repository:'+repository+':pull'})
    token = json.loads(metadata(token_url, {'Authorization':'Basic '+credentials}))['token']
    auth = {'Authorization':'Bearer '+token}
    base = 'https://ghcr.io/v2/' + repository
    raw = metadata(base+'/manifests/'+digest, auth|{'Accept':'application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json'})
    manifest = manifest_data(raw, digest)
    destination.mkdir(parents=True, exist_ok=True)
    if shutil.disk_usage(destination).free < 2*sum(d['size'] for d in [manifest['config'],*manifest['layers']])+1024**3:
        raise ValueError('Insufficient capacity to export the immutable image')
    with tempfile.TemporaryDirectory(prefix='registry-blobs-', dir=destination) as temporary:
        root = Path(temporary)
        manifest_file = root / descriptor({'digest':digest,'size':len(raw)})
        manifest_file.parent.mkdir(parents=True);manifest_file.write_bytes(raw)
        def blob(item):
            path = root/descriptor(item)
            if path.exists(): return path
            url = base+'/blobs/'+item['digest'];headers = auth
            try:
                response = registry.open(urllib.request.Request(url, headers=auth), timeout=45)
            except urllib.error.HTTPError as error:
                if error.code not in (302,307): raise RuntimeError('Registry blob lookup HTTP '+str(error.code)) from None
                url = error.headers.get('Location','');parsed = urllib.parse.urlparse(url)
                if parsed.scheme!='https' or parsed.hostname!='pkg-containers.githubusercontent.com' or parsed.username or parsed.password:
                    raise ValueError('Unexpected blob delivery destination')
                # A separate request contains no registry bearer or repository credential.
                response = delivery.open(urllib.request.Request(url), timeout=45)
            received = 0
            with response, path.open('wb') as output:
                while chunk := response.read(1024**2):
                    received += len(chunk)
                    if received>item['size']: raise ValueError('Blob content size bound exceeded')
                    output.write(chunk)
            with path.open('rb') as stream:
                if received!=item['size'] or checksum(stream)!=item['digest']: raise ValueError('Registry blob checksum mismatch')
            return path
        config = json.loads(blob(manifest['config']).read_bytes())
        diff_ids = validate_config(config,revision,len(manifest['layers']))
        unpacked=0
        for layer, diff_id in zip(manifest['layers'],diff_ids):
            path=blob(layer)
            with gzip.open(path,'rb') as stream:
                actual,length=checked_content(stream)
                if actual!=diff_id: raise ValueError('Uncompressed layer checksum mismatch')
                unpacked+=length
                if unpacked>MAX_IMAGE*2: raise ValueError('Image unpacked size bound exceeded')
        index = {'schemaVersion':2,'manifests':[{'mediaType':manifest['mediaType'],'digest':digest,'size':len(raw),
                 'annotations':{'io.containerd.image.name':reference,'org.opencontainers.image.ref.name':'sha-'+revision}}]}
        archive = destination/'image.tar'
        with tarfile.open(archive,'w') as output:
            for name, value in [('oci-layout',{'imageLayoutVersion':'1.0.0'}),('index.json',index)]:
                data=json.dumps(value,separators=(',',':')).encode();member=tarfile.TarInfo(name);member.size=len(data);output.addfile(member,io.BytesIO(data))
            for path in sorted((root/'blobs/sha256').iterdir()):
                output.add(path,arcname=path.relative_to(root).as_posix())
        with archive.open('rb') as stream: archive_sha=checksum(stream,MAX_IMAGE+8*1024**2).split(':')[1]
        record=dict(image=reference,commit=revision,archiveSha256=archive_sha,archiveBytes=archive.stat().st_size,unpackedBytes=unpacked,configDigest=manifest['config']['digest'],format='oci-layout-1.0.0')
        (destination/'manifest.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
        print('IMMUTABLE_PUBLISHED_IMAGE_ARCHIVE',json.dumps(record),flush=True)

def validate_archive(path, reference, revision):
    _, digest = reference_parts(reference)
    with tarfile.open(path) as archive:
        members = archive.getmembers();names=[m.name for m in members]
        if len(names)!=len(set(names)) or len(names)>36 or sum(m.size for m in members)>MAX_IMAGE+8*1024**2:
            raise ValueError('OCI archive content bound exceeded')
        if any(not m.isfile() or (m.name not in {'oci-layout','index.json'} and not re.fullmatch(r'blobs/sha256/[0-9a-f]{64}',m.name)) for m in members):
            raise ValueError('Unexpected OCI archive member')
        if json.load(archive.extractfile('oci-layout'))!={'imageLayoutVersion':'1.0.0'}: raise ValueError('Invalid OCI image layout')
        index=json.load(archive.extractfile('index.json'))
        roots=index.get('manifests',[])
        if index.get('schemaVersion')!=2 or len(roots)!=1 or roots[0]['digest']!=digest or roots[0].get('annotations',{}).get('io.containerd.image.name')!=reference:
            raise ValueError('OCI archive image reference mismatch')
        raw=archive.extractfile(descriptor(roots[0])).read(4*1024**2+1)
        if len(raw) != roots[0]['size']: raise ValueError('OCI root descriptor size mismatch')
        manifest=manifest_data(raw,digest)
        expected={'oci-layout','index.json',descriptor(roots[0])}
        for item in [manifest['config'],*manifest['layers']]:
            name=descriptor(item);expected.add(name)
            if archive.getmember(name).size!=item['size'] or checksum(archive.extractfile(name))!=item['digest']:
                raise ValueError('OCI image blob checksum mismatch')
        if set(names)!=expected: raise ValueError('Unexpected unreferenced OCI image content')
        config=json.load(archive.extractfile(descriptor(manifest['config'])))
        ids=validate_config(config,revision,len(manifest['layers']))
        unpacked=0
        for layer, diff_id in zip(manifest['layers'],ids):
            with gzip.GzipFile(fileobj=archive.extractfile(descriptor(layer))) as stream:
                actual,length=checked_content(stream)
                if actual!=diff_id: raise ValueError('Uncompressed OCI layer checksum mismatch')
                unpacked+=length
                if unpacked>MAX_IMAGE*2: raise ValueError('OCI image unpacked size bound exceeded')
        return config,unpacked

def load(reference, revision, archive_sha, size):
    _, digest=reference_parts(reference)
    if not re.fullmatch(r'[0-9a-f]{64}',archive_sha) or not 0<size<=MAX_IMAGE+8*1024**2:
        raise ValueError('Expected the reviewed CI archive checksum and size')
    with tempfile.TemporaryDirectory(prefix='reviewed-production-image-') as temporary:
        root=Path(temporary)
        if shutil.disk_usage(root).free<2*size+3*1024**3: raise ValueError('Insufficient reserve for image preload and rollback')
        path=root/'image.tar';value=hashlib.sha256();received=0;deadline=time.monotonic()+600
        opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect)
        url='http://192.168.2.144:18791/image-'+digest.split(':')[1]+'.tar'
        with opener.open(url,timeout=15) as response,path.open('wb') as output:
            if response.status!=200 or response.headers.get('Content-Length')!=str(size): raise ValueError('Unexpected reviewed image archive size')
            while chunk:=response.read(1024**2):
                received+=len(chunk)
                if received>size or time.monotonic()>deadline: raise ValueError('Image archive transfer bound exceeded')
                value.update(chunk);output.write(chunk)
        if received!=size or value.hexdigest()!=archive_sha: raise ValueError('Reviewed image archive checksum mismatch')
        config,unpacked=validate_archive(path,reference,revision)
        if shutil.disk_usage(root).free<unpacked+size+3*1024**3:
            raise ValueError('Insufficient reserve to import the verified image and retain rollback capacity')
        subprocess.run(['docker','load','--input',str(path)],check=True,timeout=600)
        image=json.loads(subprocess.check_output(['docker','image','inspect',reference],text=True,timeout=30))[0]
        if image['Id']!=digest or reference not in image.get('RepoDigests',[]) or image['RootFS']['Layers']!=config['rootfs']['diff_ids']:
            raise ValueError('Loaded OCI image does not preserve the immutable registry reference')
        if image['Config'].get('Labels',{}).get('org.opencontainers.image.revision')!=revision:
            raise ValueError('Loaded image revision differs from the tested commit')
        print('VERIFIED_DEPLOYMENT_PRELOAD_READY',reference,revision,flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation',choices=['export','load']);parser.add_argument('image');parser.add_argument('revision')
    parser.add_argument('--destination',type=Path);parser.add_argument('--archive-sha256');parser.add_argument('--archive-bytes',type=int)
    args=parser.parse_args()
    try:
        if args.operation=='export':
            if args.destination is None: parser.error('--destination is required for export')
            export(args.image,args.revision,args.destination)
        else: load(args.image,args.revision,args.archive_sha256 or '',args.archive_bytes or 0)
    except Exception as error:
        # Network exceptions can contain signed CDN URLs; never emit credentials or URLs.
        print('IMMUTABLE_IMAGE_ARCHIVE_FAILED',type(error).__name__,flush=True)
        raise SystemExit(1)
