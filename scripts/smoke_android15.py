#!/usr/bin/env python3
"""Launch the signed release APK on Android 15; retain evidence without signing in."""
from pathlib import Path
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'com.kvant.messenger.ayu'
OUT = Path('startup-checks')


def adb(*args, check=True):
    return subprocess.run(['adb', *args], check=check, capture_output=True).stdout.decode('utf-8', 'replace')


def capture(label):
    log = adb('logcat', '-d', '-v', 'threadtime')
    (OUT / (label + '-logcat.txt')).write_text(log, encoding='utf-8')
    activities = adb('shell', 'dumpsys', 'activity', 'activities')
    (OUT / (label + '-activities.txt')).write_text(activities, encoding='utf-8')
    pid = adb('shell', 'pidof', PACKAGE, check=False).strip()
    adb('shell', 'screencap', '-p', '/sdcard/kvant-start.png')
    adb('pull', '/sdcard/kvant-start.png', str(OUT / (label + '.png')))
    adb('shell', 'uiautomator', 'dump', '/sdcard/kvant-window.xml', check=False)
    xml = adb('shell', 'cat', '/sdcard/kvant-window.xml', check=False)
    (OUT / (label + '-window.xml')).write_text(xml, encoding='utf-8')
    crashed = bool(re.search(r'FATAL EXCEPTION:.*?Process: ' + re.escape(PACKAGE), log, re.S))
    native_crash = any('Fatal signal' in line and PACKAGE in line for line in log.splitlines())
    resumed = any(PACKAGE in line and 'ResumedActivity' in line for line in activities.splitlines())
    result = {'stage': label, 'pid': pid, 'resumed': resumed, 'crashed': crashed or native_crash}
    checks.append(result)
    print(json.dumps(result))
    assert pid and resumed and not result['crashed'], 'Release APK did not remain open: ' + label
    return xml


parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
args = parser.parse_args()
OUT.mkdir(exist_ok=True)
checks = []
try:
    (OUT / 'device.txt').write_text(adb('shell', 'getprop') + '\nPAGE_SIZE=' +
                                    adb('shell', 'getconf', 'PAGE_SIZE'), encoding='utf-8')
    print(adb('install', '-r', str(args.apk)))
    for launch in range(1, 4):
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('logcat', '-c')
        recording = None
        if launch == 1:
            recording = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '12',
                                          '/sdcard/kvant-startup.mp4'], stdout=subprocess.DEVNULL,
                                         stderr=subprocess.DEVNULL)
            time.sleep(.5)
        print(adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.MAIN',
                  '-c', 'android.intent.category.LAUNCHER', '-n', PACKAGE + '/org.telegram.ui.LaunchActivity'))
        time.sleep(25 if launch == 1 else 12)
        xml = capture('cold-start-' + str(launch))
        if recording:
            recording.wait(timeout=20)
            adb('pull', '/sdcard/kvant-startup.mp4', str(OUT / 'startup-animation.mp4'))
    # Reach the phone-number screen without entering credentials or sending anything.
    root = ET.fromstring(xml)
    buttons = [n for n in root.iter('node') if n.get('text', '').strip().lower() in
               ('start messaging', 'начать общение')]
    assert buttons, 'Welcome screen did not expose its start button'
    bounds = list(map(int, re.findall(r'\d+', buttons[0].get('bounds', ''))))
    assert len(bounds) == 4
    adb('shell', 'input', 'tap', str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
    time.sleep(12)
    capture('phone-number-screen')
finally:
    (OUT / 'result.json').write_text(json.dumps(checks, indent=2), encoding='utf-8')
