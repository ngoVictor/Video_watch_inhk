"""Generate an update manifest only after verifying the actual APK identity."""
import argparse,json,os,pathlib,tempfile
from urllib.parse import urlparse
from release_metadata import inspect,validate,ROOT

def build_manifest(info,url,version_code,version_name):
    validate(info,version_code,version_name)
    parsed=urlparse(url)
    if parsed.scheme!='https' or not parsed.hostname or parsed.username is not None or parsed.fragment or len(url)>2048:raise ValueError('APK URL must be HTTPS without credentials or fragment')
    return dict(schemaVersion=1,versionCode=info['versionCode'],versionName=info['versionName'],minSdk=info['minSdk'],apkUrl=url,sha256=info['sha256'],notes='家看电视试播版；频道覆盖及盒子兼容性请见发布说明。')

def main():
    p=argparse.ArgumentParser();p.add_argument('--apk',type=pathlib.Path,required=True);p.add_argument('--url',required=True)
    p.add_argument('--version-code',type=int,required=True);p.add_argument('--version-name',required=True)
    p.add_argument('--output',type=pathlib.Path,default=ROOT/'publishing/update.json');p.add_argument('--dry-run',action='store_true');args=p.parse_args()
    try:
        manifest=build_manifest(inspect(args.apk),args.url,args.version_code,args.version_name)
        if args.output.exists():
            old=json.loads(args.output.read_text(encoding='utf-8'))
            if manifest['versionCode']<old['versionCode'] or (manifest['versionCode']==old['versionCode'] and manifest['sha256']!=old['sha256']):raise ValueError('Refusing a downgrade or different APK at the same version')
        if args.dry_run:print(json.dumps(manifest,ensure_ascii=True));return
        fd,tmp=tempfile.mkstemp(dir=args.output.parent,suffix='.tmp')
        try:
            with os.fdopen(fd,'w',encoding='utf-8',newline='\n') as f:json.dump(manifest,f,ensure_ascii=False,indent=2);f.write('\n')
            os.replace(tmp,args.output)
        finally:pathlib.Path(tmp).unlink(missing_ok=True)
        print(args.output)
    except (ValueError,OSError) as e:p.exit(1,str(e)+'\n')
if __name__=='__main__':main()
