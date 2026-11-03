"""Bundle distributable project and pinned dependency sources; never include ignored inputs."""
import argparse, hashlib, json, os, pathlib, subprocess, tempfile, zipfile

from public_artifact import check_public_catalog

ROOT = pathlib.Path(__file__).resolve().parents[1]


def package(output, root=ROOT):
    output = pathlib.Path(output).resolve()
    files = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=root).decode('utf-8').split('\0')
    dependencies = json.loads((root/'publishing/audio-dependency-sources.json').read_text(encoding='utf-8'))
    for row in dependencies:
        path = root/'artifacts/source-dependencies'/row['file']
        if hashlib.sha256(path.read_bytes()).hexdigest() != row['sha256']:
            raise ValueError('Dependency source hash mismatch: '+row['file'])
    sources = []
    sidecar = output.with_suffix(output.suffix+'.sha256')
    for name in sorted(set(files)):
        if not name:
            continue
        path = pathlib.PurePosixPath(name)
        if any(part in ('.tools', '.private', '.git', '.gradle', 'build', 'artifacts') for part in path.parts) or path.suffix in ('.jks', '.keystore') or path.name in ('local.properties', 'credentials.json', 'catalog-config.json', 'private-update-token.txt', 'github-token.txt'):
            raise ValueError('Private/build input must not be tracked: '+name)
        if (root/name).resolve() in (output, sidecar):
            raise ValueError('Output must be outside distributable project inputs')
        if (root/name).is_file():
            if name == 'app/src/main/assets/channels.json':
                check_public_catalog((root/name).read_bytes())
            sources.append(name)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.source-package-', dir=output.parent) as staging:
        candidate = pathlib.Path(staging)/'source.zip'
        with zipfile.ZipFile(candidate, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for name in sources:
                archive.write(root/name, 'project/'+name)
            for row in dependencies:
                archive.write(root/'artifacts/source-dependencies'/row['file'], 'source-dependencies/'+row['file'])
            # Fixed timestamp keeps a retry byte-identical when source inputs have not changed.
            build_info = zipfile.ZipInfo('BUILD.txt', (2026, 1, 1, 0, 0, 0))
            archive.writestr(build_info, 'Project build and dependency details: project/docs/development/06-build-and-release.md\n'
                          'Pinned upstream source archives and hashes: project/publishing/audio-dependency-sources.json\n'
                          'Jellyfin build scripts are included in its source archive. No dependencies were modified.\n'
                          'Use your own signing key for independently modified builds. Private update credentials are omitted.\n')
        with zipfile.ZipFile(candidate) as archive:
            if archive.testzip() is not None:
                raise ValueError('Source archive CRC verification failed')
        digest = hashlib.sha256(candidate.read_bytes()).hexdigest()
        manifest = pathlib.Path(staging)/'source.sha256'
        manifest.write_text(digest+'  '+output.name+'\n', encoding='utf-8')
        try:
            # Same-filesystem hard link commits a complete file without overwriting an existing one.
            os.link(candidate, output)
        except FileExistsError:
            if hashlib.sha256(output.read_bytes()).hexdigest() != digest:
                raise ValueError('Existing source archive differs; choose a new version output path')
        os.replace(manifest, sidecar)
    return digest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    digest = package(args.output)
    print(str(args.output), args.output.stat().st_size, digest)


if __name__ == '__main__':
    main()
