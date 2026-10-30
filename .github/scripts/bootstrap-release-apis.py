"""Install API prerequisites locally to break Platform's release dependency cycles."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
# Pin upstream sources so bootstrap inputs cannot change during a release run.
PREREQUISITES = (
    ('eidos', 'f9a720d194385b14aef107003be9f680c1ec672b', 'pom.xml', 'api'),
    ('neocortex', 'e0f415e8407a611701d6c18b00dc8a18bba3b00c', 'pom.xml', 'memory-api'),
    ('casehub-pages', '6906aafd3b91c0ceb56b2822a58a8c49dac295bb', 'backend/pom.xml', 'push'),
)


def run(*argv, cwd):
    subprocess.run(argv, cwd=cwd, check=True)


def version(pom):
    root = ET.parse(pom).getroot()
    v = root.findtext('{*}version')
    if not v:
        parent = root.find('{*}parent')
        if parent is not None:
            v = parent.findtext('{*}version')
    return v


def install(root, pom, modules):
    # Only prepare dependency JARs here. The following normal Maven build runs
    # Platform's tests, including platform-api, without skip flags.
    run('mvn', '--batch-mode', '--no-transfer-progress', '-f', pom,
        'install', '-pl', modules, '-am', '-DskipTests', cwd=root)


def main():
    target = version(ROOT / 'pom.xml')
    if not target:
        raise ValueError('Platform project version is required')
    install(ROOT, 'pom.xml', 'platform-api')
    with tempfile.TemporaryDirectory(prefix='platform-api-bootstrap-') as tmp:
        for repo, revision, pom, modules in PREREQUISITES:
            root = Path(tmp) / repo
            root.mkdir()
            run('git', 'init', '--quiet', cwd=root)
            run('git', 'remote', 'add', 'origin', f'https://github.com/casehubio/{repo}.git', cwd=root)
            run('git', 'fetch', '--depth=1', 'origin', revision, cwd=root)
            run('git', 'checkout', '--detach', 'FETCH_HEAD', cwd=root)
            source = version(root / pom)
            if not source:
                raise ValueError(f'{repo}: source project version is required')
            # These pinned API builds use one coordinated CaseHub source version.
            # Modify only the temporary checkouts; nothing is committed or deployed.
            for path in root.rglob('pom.xml'):
                path.write_text(path.read_text().replace(source, target))
            install(root, pom, modules)


if __name__ == '__main__':
    main()
