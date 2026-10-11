"""Integrity and provenance controls for the reviewed deployment transfer."""
import gzip
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('registry_image_archive', Path(__file__).with_name('registry-image-archive.py'))
transport = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transport)
REVISION = 'a' * 40
REPOSITORY = 'ghcr.io/ningchirsty/hotter-ai-platform-backend'

def digest(data):
    return 'sha256:' + hashlib.sha256(data).hexdigest()

def fixture(mutation=None):
    raw_layer = b'bounded exact tested runtime content'
    layer = gzip.compress(raw_layer, mtime=0)
    config = json.dumps({'architecture': 'amd64', 'os': 'linux',
        'config': {'Labels': {'org.opencontainers.image.revision': REVISION}},
        'rootfs': {'type': 'layers', 'diff_ids': [digest(raw_layer)]}}).encode()
    manifest = json.dumps({'schemaVersion': 2, 'mediaType': 'application/vnd.oci.image.manifest.v1+json',
        'config': {'mediaType': 'application/vnd.oci.image.config.v1+json', 'digest': digest(config), 'size': len(config)},
        'layers': [{'mediaType': 'application/vnd.oci.image.layer.v1.tar+gzip', 'digest': digest(layer), 'size': len(layer)}]}).encode()
    reference = REPOSITORY + '@' + digest(manifest)
    index = {'schemaVersion': 2, 'manifests': [{'digest': digest(manifest), 'size': len(manifest),
        'annotations': {'io.containerd.image.name': reference}}]}
    files = {'oci-layout': b'{"imageLayoutVersion":"1.0.0"}', 'index.json': json.dumps(index).encode(),
        'blobs/sha256/' + digest(manifest).split(':')[1]: manifest,
        'blobs/sha256/' + digest(config).split(':')[1]: config,
        'blobs/sha256/' + digest(layer).split(':')[1]: layer}
    if mutation:
        mutation(files, index, config, manifest, layer)
    return reference, files

class ArchiveTests(unittest.TestCase):
    def validate(self, mutation=None, revision=REVISION, reference_override=None):
        reference, files = fixture(mutation)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'image.tar'
            with tarfile.open(path, 'w') as archive:
                for name, content in files.items():
                    entry = tarfile.TarInfo(name)
                    entry.size = len(content)
                    archive.addfile(entry, io.BytesIO(content))
            return transport.validate_archive(path, reference_override or reference, revision)

    def test_exact_manifest_and_compressed_and_uncompressed_content(self):
        config, unpacked = self.validate()
        self.assertEqual(config['config']['Labels']['org.opencontainers.image.revision'], REVISION)
        self.assertEqual(unpacked, len(b'bounded exact tested runtime content'))

    def test_changed_blob_is_rejected(self):
        def mutation(files, index, config, manifest, layer):
            files['blobs/sha256/' + digest(layer).split(':')[1]] = b'x' * len(layer)
        with self.assertRaises(ValueError):
            self.validate(mutation)

    def test_wrong_revision_is_rejected(self):
        with self.assertRaises(ValueError):
            self.validate(revision='b' * 40)

    def test_wrong_manifest_is_rejected(self):
        with self.assertRaises(ValueError):
            self.validate(reference_override=REPOSITORY + '@sha256:' + 'b' * 64)

    def test_unreferenced_content_is_rejected(self):
        with self.assertRaises(ValueError):
            self.validate(lambda files, *unused: files.update({'blobs/sha256/' + 'b' * 64: b'extra'}))

    def test_path_traversal_is_rejected(self):
        with self.assertRaises(ValueError):
            self.validate(lambda files, *unused: files.update({'../image': b'unexpected'}))

    def test_unsupported_repository_is_rejected(self):
        with self.assertRaises(ValueError):
            transport.reference_parts('ghcr.io/another/repository@sha256:' + 'b' * 64)

    def test_archive_member_size_must_match_descriptor(self):
        def mutation(files, index, config, manifest, layer):
            files['blobs/sha256/' + digest(config).split(':')[1]] = config + b' '
        with self.assertRaises(ValueError):
            self.validate(mutation)

if __name__ == '__main__':
    unittest.main()
