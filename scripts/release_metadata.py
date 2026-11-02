"""Local APK identity verification. No credentials or network access."""
import argparse, hashlib, json, os, pathlib, re, subprocess
ROOT=pathlib.Path(__file__).resolve().parents[1]

def inspect(apk, root=ROOT):
    apk=pathlib.Path(apk)
    if not apk.is_file() or not 0<apk.stat().st_size<=100*1024*1024:
        raise ValueError('APK missing, empty, or larger than 100 MiB')
    env=os.environ.copy()
    if not env.get('JAVA_HOME'):
        env['JAVA_HOME']=str(next((root/'.tools/jdk').iterdir()))
    build=pathlib.Path(env.get('ANDROID_HOME',str(root/'.tools/android-sdk')))/'build-tools/35.0.0'
    signature=subprocess.run([str(build/'apksigner.bat'),'verify','--print-certs',str(apk)],capture_output=True,text=True,env=env,timeout=60)
    if signature.returncode: raise ValueError('APK signature verification failed')
    pins=re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})$',signature.stdout,re.M)
    expected=(root/'publishing/signing-certificate.sha256').read_text().strip().lower()
    if pins!=[expected]: raise ValueError('APK certificate differs from historical release')
    result=subprocess.run([str(build/'aapt.exe'),'dump','badging',str(apk)],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=30)
    if result.returncode: raise ValueError('Cannot read APK metadata')
    package=re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'",result.stdout,re.M)
    sdk=re.search(r"^sdkVersion:'(\d+)'",result.stdout,re.M)
    if not package or not sdk: raise ValueError('Missing APK version or SDK metadata')
    info=dict(packageName=package[1],versionCode=int(package[2]),versionName=package[3],minSdk=int(sdk[1]),certificate=expected)
    validate(info)
    info['sha256']=hashlib.sha256(apk.read_bytes()).hexdigest()
    return info

def validate(info, version_code=None, version_name=None):
    if info['packageName']!='hk.jiakan.tv' or not 1<=info['versionCode']<=2147483647 or not 23<=info['minSdk']<=100:
        raise ValueError('APK package, version or SDK is invalid')
    if not re.fullmatch(r'[A-Za-z0-9._-]{1,80}',info['versionName']): raise ValueError('Invalid versionName')
    if version_code is not None and version_code!=info['versionCode']: raise ValueError('versionCode differs from APK')
    if version_name is not None and version_name!=info['versionName']: raise ValueError('versionName differs from APK')

if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('--apk',type=pathlib.Path,required=True)
    p.add_argument('--version-code',type=int);p.add_argument('--version-name');args=p.parse_args()
    try:
        info=inspect(args.apk);validate(info,args.version_code,args.version_name);print(json.dumps(info))
    except (ValueError,OSError,subprocess.SubprocessError) as e:p.exit(1,str(e)+'\n')
