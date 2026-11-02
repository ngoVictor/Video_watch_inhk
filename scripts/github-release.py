"""Repository-scoped publishing helper. Credentials stay in ignored local files, never in logs."""
import argparse
import json
import pathlib
import urllib.request
import urllib.error
import urllib.parse
import subprocess
from release_metadata import inspect as inspect_apk
from public_artifact import check_public_apk

ROOT=pathlib.Path(__file__).resolve().parents[1]
REPO='ngoVictor/Video_watch_inhk'
USE_GIT_CREDENTIAL=False

class NoApiRedirect(urllib.request.HTTPRedirectHandler):
    """Publishing JSON endpoints must never forward credentials to a redirect target."""
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise urllib.error.HTTPError(req.full_url, code, 'Publishing API redirect refused', headers, fp)


def validate_release(release, tag, apk, info):
    check_public_apk(apk)
    assets=[asset for asset in release.get('assets',[]) if asset.get('name')==apk.name]
    if release.get('tag_name')!=tag or len(assets)!=1:
        raise ValueError('Release tag or unique APK asset does not match local inputs')
    asset=assets[0]
    if (asset.get('state')!='uploaded' or asset.get('size')!=apk.stat().st_size
            or asset.get('digest')!='sha256:'+info['sha256']):
        raise ValueError('Uploaded APK state, size or SHA-256 differs or is unavailable; upload a verified asset')

def credential():
    if not USE_GIT_CREDENTIAL:
        return (ROOT/'.tools/private/github-token.txt').read_text(encoding='utf-8').strip()
    result=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\npath='+REPO+'.git\n\n',
                          text=True,capture_output=True,cwd=ROOT,timeout=60)
    if result.returncode: raise RuntimeError('Git credential lookup failed')
    fields=dict(line.split('=',1) for line in result.stdout.splitlines() if '=' in line)
    if not fields.get('password'): raise RuntimeError('Git credential unavailable')
    return fields['password']

def call(path,method='GET',payload=None,asset=None):
    host='uploads.github.com' if asset else 'api.github.com'
    repo_path='/repos/'+REPO
    if path!=repo_path and not path.startswith(repo_path+'/'): raise ValueError('Outside the authorized repository')
    if asset and asset.suffix.lower()=='.apk': check_public_apk(asset)
    token=credential()
    headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json',
             'X-GitHub-Api-Version':'2022-11-28','User-Agent':'JiaKanTV-publisher'}
    body=None
    if asset:
        body=asset.read_bytes(); headers['Content-Type']='application/vnd.android.package-archive' if asset.suffix=='.apk' else 'application/octet-stream'
    elif payload is not None:
        body=json.dumps(payload).encode(); headers['Content-Type']='application/json'
    request=urllib.request.Request('https://'+host+path,data=body,headers=headers,method=method)
    with urllib.request.build_opener(NoApiRedirect()).open(request,timeout=120) as response: return json.load(response)

if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('action',choices=['inspect','create','upload','publish','get'])
    p.add_argument('--release-id',type=int); p.add_argument('--asset',type=pathlib.Path)
    p.add_argument('--tag'); p.add_argument('--branch'); p.add_argument('--apk',type=pathlib.Path); p.add_argument('--dry-run',action='store_true')
    p.add_argument('--git-credential',action='store_true',help='Use local Git login for this repository only; never bundle it in the APK')
    args=p.parse_args()
    USE_GIT_CREDENTIAL=args.git_credential
    try:
        info=None
        if args.action in ('create','publish'):
            if not args.tag or not args.apk or (args.action=='create' and not args.branch):p.error('Explicit --tag, --apk and (for create) --branch are required')
            check_public_apk(args.apk)
            info=inspect_apk(args.apk)
            if args.tag!='v'+info['versionName']:p.error('Tag must match the verified APK versionName')
        if args.action in ('upload','publish','get') and (args.release_id is None or args.release_id<1):p.error('Positive --release-id required')
        if args.action=='upload':
            if args.asset is None or not args.asset.is_file():p.error('Existing --asset required')
            if args.asset.suffix=='.apk':info=inspect_apk(args.asset)
        if args.dry_run:
            print(json.dumps(dict(action=args.action,tag=args.tag,branch=args.branch,apk=info,network=False)));raise SystemExit(0)
        if args.action=='inspect':
            repo=call('/repos/'+REPO)
            print(json.dumps({k:repo.get(k) for k in ['full_name','private','default_branch','permissions']}))
        elif args.action=='create':
            body=(ROOT/'publishing/release-notes.md').read_text(encoding='utf-8')
            r=call('/repos/'+REPO+'/releases','POST',dict(tag_name=args.tag,target_commitish=args.branch,
                name='家看电视 '+args.tag,body=body,draft=True,prerelease=True))
            print(json.dumps({k:r.get(k) for k in ['id','html_url','draft']}))
        elif args.action=='upload':
            r=call('/repos/'+REPO+f'/releases/{args.release_id}/assets?name='+urllib.parse.quote(args.asset.name), 'POST',asset=args.asset)
            print(json.dumps({k:r.get(k) for k in ['id','name','url','browser_download_url','size']}))
        elif args.action=='publish':
            release=call('/repos/'+REPO+f'/releases/{args.release_id}')
            validate_release(release,args.tag,args.apk,info)
            r=call('/repos/'+REPO+f'/releases/{args.release_id}','PATCH',dict(draft=False))
            print(json.dumps({k:r.get(k) for k in ['id','html_url','draft']}))
        else:
            r=call('/repos/'+REPO+f'/releases/{args.release_id}')
            print(json.dumps({'id':r['id'],'draft':r['draft'],'assets':[{k:a.get(k) for k in ['id','name','url','size']} for a in r['assets']]}))
    except urllib.error.HTTPError as error:
        print('GitHub API request failed: HTTP',error.code)
        raise SystemExit(1)
    except (ValueError,OSError,subprocess.SubprocessError) as error:
        p.exit(1,str(error)+'\n')
