"""Reject private catalog material before any public APK upload."""
import json, zipfile
def check_public_apk(path):
    with zipfile.ZipFile(path) as apk:
        names=set(apk.namelist())
        if any(name.endswith(('/private-update-token.txt','/catalog-config.json')) for name in names):
            raise ValueError('Private profile must not be published')
        if json.loads(apk.read('assets/channels.json'))['channels']:
            raise ValueError('Public APK must not bundle a private catalog')
