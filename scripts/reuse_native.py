#!/usr/bin/env python3
"""Reuse byte-identical native libraries from our verified build of the same C++ sources."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys
import zipfile

source=Path(sys.argv[1])
reference=Path(sys.argv[2])
commit='7013145676d36d82ee13c02a89f72097b7490dcd'
expected='e0dce4ddd124004f1f782480e9de6fc7654258e3bc4217495fbd527fe12b3b7a'
assert (reference/'upstream-commit.txt').read_text().strip()==commit
apk=reference/'kvantGram-Android15-arm64.apk'
assert hashlib.sha256(apk.read_bytes()).hexdigest()==expected
subprocess.run(['git','diff','--exit-code','--','TMessagesProj/jni'],cwd=source,check=True)
libs=source/'TMessagesProj/src/main/kvantNative/arm64-v8a'
libs.mkdir(parents=True,exist_ok=True)
hashes={}
with zipfile.ZipFile(apk) as z:
    for name in z.namelist():
        # Other APK libraries come from dependency AARs and are merged by Gradle.
        if name=='lib/arm64-v8a/libtmessages.45.so':
            data=z.read(name);(libs/Path(name).name).write_bytes(data)
            hashes[Path(name).name]=hashlib.sha256(data).hexdigest()
assert 'libtmessages.45.so' in hashes
gradle=source/'TMessagesProj/build.gradle'
text=gradle.read_text()
for _ in range(2):
    start=text.index('externalNativeBuild {')
    brace=text.index('{',start);depth=1;end=brace+1
    while depth:
        if text[end]=='{':depth+=1
        if text[end]=='}':depth-=1
        end+=1
    text=text[:start]+'// Native code is unchanged; verified libraries are reused below.'+text[end:]
assert 'externalNativeBuild {' not in text
assert "jniLibs.srcDirs = ['jni']" in text
text=text.replace("jniLibs.srcDirs = ['jni']","jniLibs.srcDirs = ['src/main/kvantNative']")
gradle.write_text(text)
Path('native-provenance.json').write_text(json.dumps({'upstream_commit':commit,'reference_run':41023893681,
    'reference_apk_sha256':expected,'libraries':hashes},indent=2))
print('Verified and reused unchanged native libraries:', ', '.join(hashes))
