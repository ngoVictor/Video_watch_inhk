import importlib.util,pathlib,sys,unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
from release_metadata import validate as validate_apk
class ReleaseMetadataTest(unittest.TestCase):
 def test_matching_metadata(self):validate_apk(dict(packageName='hk.jiakan.tv',versionCode=6,versionName='0.2.1-alpha',minSdk=23),6,'0.2.1-alpha')
 def test_mismatched_metadata(self):
  valid=dict(packageName='hk.jiakan.tv',versionCode=6,versionName='0.2.1-alpha',minSdk=23)
  for code,name in ((5,None),(None,'old')):
   with self.assertRaises(ValueError):validate_apk(valid,code,name)
  for field,value in [('packageName','other'),('minSdk',1),('versionCode',0)]:
   with self.assertRaises(ValueError):validate_apk(dict(valid,**{field:value}))
class UpdateManifestToolTest(unittest.TestCase):
 def setUp(self):
  spec=importlib.util.spec_from_file_location('update_manifest',ROOT/'scripts/make-update-manifest.py');self.m=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.m)
  self.info=dict(packageName='hk.jiakan.tv',versionCode=6,versionName='0.2.1-alpha',minSdk=24,sha256='a'*64)
 def test_manifest_uses_apk_sdk(self):
  result=self.m.build_manifest(self.info,'https://example.org/app.apk',6,'0.2.1-alpha');self.assertEqual(24,result['minSdk'])
 def test_invalid_url_and_version_do_not_generate(self):
  for url in ['http://example.org/app.apk','https://user:secret@example.org/app.apk','https://example.org/app.apk#fragment']:
   with self.assertRaises(ValueError):self.m.build_manifest(self.info,url,6,'0.2.1-alpha')
  with self.assertRaises(ValueError):self.m.build_manifest(self.info,'https://example.org/app.apk',5,'0.2.1-alpha')
if __name__=='__main__':unittest.main()
