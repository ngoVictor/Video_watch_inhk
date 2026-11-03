import hashlib
from email.message import Message
import importlib.util
import io
import json
import pathlib
import sys
import tempfile
import unittest
import urllib.error
import urllib.request
import urllib.response
import zipfile
from unittest.mock import patch

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'scripts'))


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT/'scripts'/filename)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


publisher = module('publisher_safety', 'github-release.py')
packager = module('packager_safety', 'package-source.py')


class PublishingSafetyTest(unittest.TestCase):
    def test_opener_stops_before_redirect_target_is_contacted(self):
        visited = []

        class SyntheticHttps(urllib.request.HTTPSHandler):
            def https_open(self, request):
                visited.append(request.full_url)
                headers = Message()
                headers['Location'] = 'https://example.org/credential-sink'
                response = urllib.response.addinfourl(io.BytesIO(b''), headers, request.full_url, 302)
                response.msg = 'Found'
                return response

        opener = urllib.request.build_opener(SyntheticHttps(), publisher.NoApiRedirect())
        url = 'https://api.github.com/repos/'+publisher.REPO
        request = urllib.request.Request(url, headers={'Authorization': 'Bearer synthetic-fixture'})
        with self.assertRaises(urllib.error.HTTPError):
            opener.open(request)
        self.assertEqual([url], visited)

    def test_redirects_never_create_authenticated_followup(self):
        handler = publisher.NoApiRedirect()
        request = urllib.request.Request('https://api.github.com/repos/'+publisher.REPO,
                                         headers={'Authorization': 'Bearer synthetic-fixture'})
        for status in (301, 302, 303, 307, 308):
            for target in ('https://example.org/stolen', 'http://api.github.com/downgrade',
                           'https://api.github.com/repos/another/repository'):
                with self.subTest(status=status, target=target), self.assertRaises(urllib.error.HTTPError) as error:
                    handler.redirect_request(request, None, status, 'fixture', {}, target)
                self.assertNotIn('synthetic-fixture', str(error.exception))

    def test_call_installs_redirect_guard(self):
        with patch.object(publisher, 'credential', return_value='synthetic-fixture'), \
                patch.object(publisher.urllib.request, 'build_opener') as build:
            build.return_value.open.return_value.__enter__.return_value = io.BytesIO(b'{"ok":true}')
            self.assertEqual({'ok': True}, publisher.call('/repos/'+publisher.REPO))
            self.assertIsInstance(build.call_args.args[0], publisher.NoApiRedirect)

    def test_outside_repository_rejected_before_credential_access(self):
        with patch.object(publisher, 'credential') as credential, self.assertRaises(ValueError):
            publisher.call('/repos/other/project')
        credential.assert_not_called()

    @patch.object(publisher, 'check_public_apk')
    def test_remote_apk_requires_exact_content_and_completed_unique_asset(self, profile_check):
        with tempfile.TemporaryDirectory() as directory:
            apk = pathlib.Path(directory)/'test.apk'
            apk.write_bytes(b'good')
            info = {'sha256': hashlib.sha256(b'good').hexdigest()}
            asset = dict(name=apk.name, size=4, state='uploaded', digest='sha256:'+info['sha256'])
            release = dict(tag_name='vtest', assets=[asset])
            publisher.validate_release(release, 'vtest', apk, info)
            for changes in ({'digest': 'sha256:'+hashlib.sha256(b'evil').hexdigest()},
                            {'digest': None}, {'state': 'starter'}, {'size': 3}):
                with self.subTest(changes=changes), self.assertRaises(ValueError):
                    publisher.validate_release(dict(release, assets=[dict(asset, **changes)]), 'vtest', apk, info)
            for changes in ({'tag_name': 'wrong'}, {'assets': []}, {'assets': [asset, asset]}):
                with self.subTest(changes=changes), self.assertRaises(ValueError):
                    publisher.validate_release(dict(release, **changes), 'vtest', apk, info)


class SourceArchiveSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
        (self.root/'publishing').mkdir()
        (self.root/'publishing/audio-dependency-sources.json').write_text('[]', encoding='utf-8')
        (self.root/'README.md').write_text('fixture source', encoding='utf-8')
        self.output = self.root/'artifacts/source.zip'
        self.output.parent.mkdir()
        self.files = patch.object(packager.subprocess, 'check_output', return_value=b'README.md\0')
        self.files.start()

    def tearDown(self):
        self.files.stop()
        self.temp.cleanup()

    def clean_staging(self):
        self.assertEqual([], list(self.output.parent.glob('.source-package-*')))

    def test_success_and_identical_retry_repair_digest(self):
        digest = packager.package(self.output, self.root)
        before = self.output.read_bytes()
        sidecar = self.output.with_suffix('.zip.sha256')
        sidecar.unlink()
        self.assertEqual(digest, packager.package(self.output, self.root))
        self.assertEqual(before, self.output.read_bytes())
        self.assertEqual(digest+'  source.zip\n', sidecar.read_text())
        with zipfile.ZipFile(self.output) as archive:
            self.assertIsNone(archive.testzip())
            self.assertEqual(b'fixture source', archive.read('project/README.md'))
        self.clean_staging()

    def test_existing_different_archive_and_digest_preserved(self):
        self.output.write_bytes(b'old archive')
        sidecar = self.output.with_suffix('.zip.sha256')
        sidecar.write_text('old digest')
        with self.assertRaises(ValueError):
            packager.package(self.output, self.root)
        self.assertEqual(b'old archive', self.output.read_bytes())
        self.assertEqual('old digest', sidecar.read_text())
        self.clean_staging()

    def test_write_failure_preserves_old_archive(self):
        self.output.write_bytes(b'old archive')
        with patch.object(packager.zipfile.ZipFile, 'write', side_effect=OSError('injected write failure')), self.assertRaises(OSError):
            packager.package(self.output, self.root)
        self.assertEqual(b'old archive', self.output.read_bytes())
        self.clean_staging()

    def test_crc_failure_does_not_commit(self):
        with patch.object(packager.zipfile.ZipFile, 'testzip', return_value='README.md'), self.assertRaises(ValueError):
            packager.package(self.output, self.root)
        self.assertFalse(self.output.exists())
        self.clean_staging()

    def test_private_catalog_rejected_before_touching_output(self):
        path=self.root/'app/src/main/assets/channels.json'
        path.parent.mkdir(parents=True)
        path.write_text('{"channels":[{"id":"private-fixture"}]}')
        self.output.write_bytes(b'old archive')
        with patch.object(packager.subprocess,'check_output',return_value=b'app/src/main/assets/channels.json\0'), self.assertRaises(ValueError):
            packager.package(self.output,self.root)
        self.assertEqual(b'old archive',self.output.read_bytes())

    def test_forced_private_directory_is_rejected(self):
        with patch.object(packager.subprocess,'check_output',return_value=b'.private/profile.json\0'), self.assertRaises(ValueError):
            packager.package(self.output,self.root)
        self.assertFalse(self.output.exists())

    def test_private_input_rejected_before_touching_output(self):
        self.output.write_bytes(b'old archive')
        with patch.object(packager.subprocess, 'check_output', return_value=b'private-update-token.txt\0'), self.assertRaises(ValueError):
            packager.package(self.output, self.root)
        self.assertEqual(b'old archive', self.output.read_bytes())
        self.clean_staging()

    def test_digest_commit_failure_leaves_complete_retryable_zip(self):
        with patch.object(packager.os, 'replace', side_effect=OSError('injected digest failure')), self.assertRaises(OSError):
            packager.package(self.output, self.root)
        with zipfile.ZipFile(self.output) as archive:
            self.assertIsNone(archive.testzip())
        self.clean_staging()
        digest = packager.package(self.output, self.root)
        self.assertEqual(digest, hashlib.sha256(self.output.read_bytes()).hexdigest())


if __name__ == '__main__':
    unittest.main()
