"""Retain publication only while a purpose, its controls, and its graph match verified evidence."""
import hashlib
import json
import re

def schema_checksum(ability, binding):
    purpose = {key:value for key,value in ability.items() if key not in ('workflows','status','recommendedWorkflowCode','sources')}
    workflow = {key:value for key,value in binding.items() if key not in ('verifiedAt','status','publicationEvidence')}
    canonical = json.dumps([purpose,workflow],ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()
    return hashlib.sha256(canonical).hexdigest()

def approved(entry, ability, binding):
    if not entry:
        return False
    return all([
        entry.get('media') == ability['media'],
        entry.get('workflowCode') == binding['workflowCode'],
        entry.get('apiChecksum') == binding['apiChecksum'],
        entry.get('schemaChecksum') == schema_checksum(ability,binding),
        entry.get('technicalAcceptance') == 'PASSED',
        bool(re.fullmatch(r'[0-9a-f]{40}',str(entry.get('testedCommit','')))),
        bool(re.fullmatch(r'[0-9a-f]{64}',str(entry.get('outputSha256','')))),
        bool(re.fullmatch(r'[0-9a-f-]{36}',str(entry.get('comfyPromptId','')))),
        bool(re.fullmatch(r'[1-9][0-9]*',str(entry.get('taskId','')))),
        bool(re.fullmatch(r'https://github\.com/Ningchirsty/hotter-ai-platform/actions/runs/[1-9][0-9]*',str(entry.get('pipelineUrl','')))),
        bool(entry.get('verifiedAt')),
    ])

def apply_publication(root, definitions, contracts):
    file = root / 'script/creative-ability-publication.json'
    entries = json.loads(file.read_text(encoding='utf8'))['workflows'] if file.exists() else []
    by_code = {entry['workflowCode']:entry for entry in entries}
    if len(by_code) != len(entries):
        raise ValueError('Duplicate publication evidence')
    bindings = {binding['workflowCode']:binding for caps in contracts.values() for cap in caps for binding in cap['workflows']}
    for ability in definitions:
        any_published = False
        for binding in ability['workflows']:
            entry = by_code.get(binding['workflowCode'])
            published = approved(entry,ability,binding)
            bindings[binding['workflowCode']]['status'] = 'PUBLISHED' if published else 'DRAFT'
            if published:
                binding['verifiedAt'] = entry['verifiedAt']
                binding['publicationEvidence'] = dict(pipelineUrl=entry['pipelineUrl'],testedCommit=entry['testedCommit'],taskId=entry['taskId'])
                any_published = True
            else:
                binding['verifiedAt'] = None
                binding.pop('publicationEvidence',None)
        ability['status'] = 'PUBLISHED' if any_published else 'DRAFT'
