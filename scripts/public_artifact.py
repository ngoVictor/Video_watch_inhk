"""Reject private catalog material before any public APK upload."""
import json, zipfile
def check_public_catalog(raw):
    document=json.loads(raw)
    if document.get('channels') != []:
        raise ValueError('Public distribution must not bundle a private catalog')

def check_public_apk(path):
    with zipfile.ZipFile(path) as apk:
        names=set(apk.namelist())
        if any(name.endswith(('/private-update-token.txt','/catalog-config.json')) for name in names):
            raise ValueError('Private profile must not be published')
        check_public_catalog(apk.read('assets/channels.json'))
