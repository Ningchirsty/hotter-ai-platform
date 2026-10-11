import copy
import json
from pathlib import Path
import tempfile
import unittest
import runpy

from creative_publication import apply_publication, approved, schema_checksum

class PublicationEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.ability = dict(code='POSTER',media='image',promptTemplate='Title: ${title}',inputs=[dict(key='title',required=True)],assets=[])
        self.binding = dict(workflowCode='wf-ability-image-poster-test',apiChecksum='a'*64,fields=['prompt','size'],sizes=[dict(label='square',width=512,height=512)])
        self.ability['workflows'] = [self.binding]
        self.entry = dict(workflowCode=self.binding['workflowCode'],media='image',apiChecksum='a'*64,
                          schemaChecksum=schema_checksum(self.ability,self.binding),technicalAcceptance='PASSED',testedCommit='b'*40,
                          outputSha256='c'*64,comfyPromptId='00000000-0000-0000-0000-000000000001',taskId=123,
                          pipelineUrl='https://github.com/Ningchirsty/hotter-ai-platform/actions/runs/123',verifiedAt='2026-10-11T00:00:00Z')

    def test_graph_or_purpose_change_revokes_previous_approval(self):
        self.assertTrue(approved(self.entry,self.ability,self.binding))
        for mutated in [dict(self.binding,apiChecksum='d'*64),dict(self.binding,sizes=[dict(label='wide',width=1024,height=512)])]:
            self.assertFalse(approved(self.entry,self.ability,mutated))
        self.assertFalse(approved(self.entry,dict(self.ability,promptTemplate='Different layout'),self.binding))
        self.assertFalse(approved(self.entry,dict(self.ability,media='video'),self.binding))

    def test_incomplete_or_failed_platform_trace_cannot_publish(self):
        for key in ['taskId','comfyPromptId','outputSha256','testedCommit','pipelineUrl','verifiedAt']:
            entry = dict(self.entry)
            entry.pop(key)
            self.assertFalse(approved(entry,self.ability,self.binding),key)
        self.assertFalse(approved(dict(self.entry,technicalAcceptance='FAILED'),self.ability,self.binding))

    def test_file_publication_updates_backend_and_public_schema_together(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root/'script').mkdir()
            (root/'script/creative-ability-publication.json').write_text(json.dumps(dict(workflows=[self.entry])))
            native = dict(workflowCode=self.binding['workflowCode'],status='DRAFT')
            contracts = {'image':[dict(workflows=[native])],'video':[]}
            apply_publication(root,[self.ability],contracts)
            self.assertEqual('PUBLISHED',native['status'])
            self.assertEqual('PUBLISHED',self.ability['status'])
            self.assertEqual(123,self.binding['publicationEvidence']['taskId'])
            self.ability['inputs'][0]['required'] = False
            apply_publication(root,[self.ability],contracts)
            self.assertEqual('DRAFT',native['status'])
            self.assertEqual('DRAFT',self.ability['status'])

    def test_missing_manifest_leaves_bindings_unpublished(self):
        with tempfile.TemporaryDirectory() as folder:
            native = dict(workflowCode=self.binding['workflowCode'],status='DRAFT')
            apply_publication(Path(folder),[self.ability],{'image':[dict(workflows=[native])]})
            self.assertEqual('DRAFT',native['status'])

    def test_stored_video_must_match_resolution_duration_frame_rate_and_required_audio(self):
        validate = runpy.run_path(str(Path(__file__).with_name('publish-creative-abilities.py')))['validate_video_output']
        supported = [dict(width=832,height=448,durationSeconds=49/24,fps=24)]
        video = dict(codec_type='video',width=832,height=448,avg_frame_rate='24/1')
        valid = dict(streams=[video,dict(codec_type='audio')],format=dict(duration='2.041667'))
        validate(valid,supported,True)
        for invalid in [dict(streams=[dict(video,width=480),dict(codec_type='audio')],format=valid['format']),
                        dict(streams=[dict(video,avg_frame_rate='30/1'),dict(codec_type='audio')],format=valid['format']),
                        dict(streams=valid['streams'],format=dict(duration='5.0')),
                        dict(streams=[video],format=valid['format'])]:
            with self.assertRaises(ValueError):
                validate(invalid,supported,True)

if __name__ == '__main__':
    unittest.main()
