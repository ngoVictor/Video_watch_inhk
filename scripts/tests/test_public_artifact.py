import json,pathlib,sys,tempfile,unittest,zipfile
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
from public_artifact import check_public_apk
class PublicArtifactTest(unittest.TestCase):
    def test_public_passes_and_private_inputs_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            path=pathlib.Path(directory)/'test.apk'
            for channels,extra in [([],None),([{'id':'private'}],None),([],'catalog-config.json'),([],'private-update-token.txt')]:
                with zipfile.ZipFile(path,'w') as archive:
                    archive.writestr('assets/channels.json',json.dumps({'channels':channels}))
                    if extra: archive.writestr('assets/'+extra,'synthetic')
                if channels or extra:
                    with self.assertRaises(ValueError): check_public_apk(path)
                else: check_public_apk(path)
