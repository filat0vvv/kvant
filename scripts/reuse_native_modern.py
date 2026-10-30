#!/usr/bin/env python3
"""Reuse only the main native library from the exact pinned upstream CI build."""
from pathlib import Path
import hashlib
import io
import json
import subprocess
import sys
import zipfile

root=Path(sys.argv[1]);reference=Path(sys.argv[2])
commit='0a4372f9b4927b014a21eaa9fc8af4b4efb6af12'
archive_sha='95aae0e97a158d658d44ce84e13e754ba43a147076aab24dc7ac0cf3e7893775'
apk_sha='9ec5cb795d5a2fb8816581144944870bb62324428c82b141cafb42b8c6eda208'
assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()==commit
subprocess.run(['git','diff','--exit-code','--','TMessagesProj/jni'],cwd=root,check=True)
if reference.is_file():
    assert hashlib.sha256(reference.read_bytes()).hexdigest()==archive_sha
    with zipfile.ZipFile(reference) as z:
        apk=z.read('NagramXTurbo-v13.1.1-T97-12610097-arm64-v8a.apk')
else:
    apk=(reference/'NagramXTurbo-v13.1.1-T97-12610097-arm64-v8a.apk').read_bytes()
assert hashlib.sha256(apk).hexdigest()==apk_sha
with zipfile.ZipFile(io.BytesIO(apk)) as z:data=z.read('lib/arm64-v8a/libtmessages.49.so')
assert data[:5]==b'\x7fELF\x02'
folder=root/'TMessagesProj/src/main/kvantNative/arm64-v8a';folder.mkdir(parents=True,exist_ok=True)
(folder/'libtmessages.49.so').write_bytes(data)
path=root/'TMessagesProj/build.gradle';text=path.read_text()
count=0
while 'externalNativeBuild {' in text:
    start=text.index('externalNativeBuild {');brace=text.index('{',start);end=brace+1;depth=1
    while depth:
        if text[end]=='{':depth+=1
        if text[end]=='}':depth-=1
        end+=1
    text=text[:start]+'// Pinned native library is supplied by kvantNative.'+text[end:];count+=1
assert count==3
text=text.replace("sourceSets.main.jniLibs.srcDirs = ['./jni/']","sourceSets.main.jniLibs.srcDirs = ['./jni/', 'src/main/kvantNative']")
path.write_text(text)
Path('native-provenance-modern.json').write_text(json.dumps({'repository':'temporaryna/NagramXTurbo',
 'commit':commit,'run':40912825852,'artifact':12916235915,'artifact_sha256':archive_sha,
 'apk_sha256':apk_sha,'library':'libtmessages.49.so','library_sha256':hashlib.sha256(data).hexdigest()},indent=2))
print('Verified native library from pinned Telegram 13.1.1 build')
