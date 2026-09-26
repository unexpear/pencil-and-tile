"""One-time setup for automatic releases: stores the upload key and its passwords as GitHub secrets.

Run it yourself (it reads ~/.pencil-and-tile/upload.properties and the keystore it names, and hands them
straight to `gh secret set`; nothing is printed):

    python tools/set_release_secrets.py
    python tools/set_release_secrets.py path/to/play-service-account.json   # also the Play upload key

Needs the GitHub CLI signed in (`gh auth login`).
"""
import base64, os, subprocess, sys

REPO = 'unexpear/pencil-and-tile'
PROPS = os.path.join(os.path.expanduser('~'), '.pencil-and-tile', 'upload.properties')


def secret(name, value):
    subprocess.run(['gh', 'secret', 'set', name, '--repo', REPO], input=value, text=True, check=True, capture_output=True)
    print(f'set {name}')


def main():
    if not os.path.exists(PROPS):
        sys.exit(f'{PROPS} not found.')
    props = {}
    for line in open(PROPS, encoding='utf-8'):
        if '=' in line and not line.lstrip().startswith('#'):
            k, v = line.split('=', 1)
            props[k.strip()] = v.strip()
    store = props['storeFile']
    if not os.path.isabs(store):
        store = os.path.join(os.path.dirname(PROPS), store)
    with open(store, 'rb') as f:
        secret('PENCILTILE_KEYSTORE_BASE64', base64.b64encode(f.read()).decode())
    secret('PENCILTILE_KEYSTORE_PASSWORD', props['storePassword'])
    secret('PENCILTILE_KEY_ALIAS', props['keyAlias'])
    secret('PENCILTILE_KEY_PASSWORD', props['keyPassword'])
    if len(sys.argv) > 1:
        with open(sys.argv[1], encoding='utf-8') as f:
            secret('PLAY_SERVICE_ACCOUNT_JSON', f.read())
    print('Done. Tag a release with: python tools/release.py X.Y.Z')


if __name__ == '__main__':
    main()
