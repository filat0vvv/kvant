#!/usr/bin/env python3
"""Build recipe for the public AyuGram Android sources and kvantGram history."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
from xml.sax.saxutils import escape
from PIL import Image
import remove_unpublished_boost

REPO = Path(__file__).resolve().parents[1]


def edit(path, old, new, expected=1):
    text = path.read_text(encoding='utf-8')
    if text.count(old) != expected:
        raise RuntimeError(f'Upstream changed: {path}: expected {expected} matches for {old[:70]!r}')
    path.write_text(text.replace(old,new),encoding='utf-8')


def apply(root):
    name = os.environ.get('APP_NAME','kvantGram Ayu')
    package = os.environ.get('APP_ID','com.kvant.messenger.ayu')
    if not re.fullmatch(r'[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*){2,}',package):
        raise ValueError('Invalid application ID')
    if package == 'com.kvant.messenger':
        raise ValueError('Use a separate package: the old Telegram-FOSS database must not be downgraded')
    if not name.strip() or any(ord(c)<32 for c in name): raise ValueError('Invalid app name')
    java = root/'TMessagesProj/src/main/java'
    remove_unpublished_boost.apply(java)
    edit(java/'org/telegram/ui/Cells/ChatMessageCell.java',
         '''        if (currentMessageObject.isDecrypted && currentMessageObject.currentEncryptor != null) {
            timeString = currentMessageObject.currentEncryptor.getName() + " " + timeString;
        }
''','')
    res = root/'TMessagesProj/src/main/res'
    gradle = root/'TMessagesProj/build.gradle'
    edit(root/'gradle.properties','APP_PACKAGE=com.radolyn.ayugram','APP_PACKAGE='+package)
    edit(root/'gradle.properties','org.gradle.unsafe.configuration-cache=true','org.gradle.unsafe.configuration-cache=false')
    edit(gradle,'storeFile file("config/extera.jks")',"storeFile file(System.getenv('KVANT_KEYSTORE_PATH'))")
    for field,key,env in [('storePassword','SIGNING_KEY_STORE_PASSWORD','ANDROID_KEYSTORE_PASSWORD'),
                          ('keyAlias','SIGNING_KEY_ALIAS','ANDROID_KEY_ALIAS'),
                          ('keyPassword','SIGNING_KEY_PASSWORD','ANDROID_KEY_PASSWORD')]:
        edit(gradle,f'{field} localProperties.getProperty("{key}")',f"{field} System.getenv('{env}')")
    for plugin in ('com.google.gms.google-services','com.google.firebase.crashlytics'):
        edit(gradle,f"apply plugin: '{plugin}'",f"// kvantGram: no upstream Firebase configuration ({plugin})")
    edit(gradle,'ndk.abiFilters "armeabi-v7a", "arm64-v8a", "x86", "x86_64"','ndk.abiFilters "arm64-v8a"')
    edit(gradle,'outputFileName = "ayuGram-${buildType}${abi}-${formattedDate}.apk"','outputFileName = "kvantGram-Ayu-arm64.apk"')
    # Pure Java/Room regression tests run after assembling; no native calls needed in the fixtures.
    with gradle.open('a',encoding='utf-8') as f:
        f.write('''
dependencies {
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.robolectric:robolectric:4.10.3'
}
android.testOptions.unitTests.includeAndroidResources = true
android.testOptions.unitTests.all { maxHeapSize = "2g" }
''')
    # Local names bypass remote language packs; regular cloud translations remain available.
    for path in res.glob('values*/*.xml'):
        text=path.read_text(encoding='utf-8')
        for key,label in [('AppName',name),('AppNameBeta',name+' Beta'),('exteraAppName',name),
                          ('Preferences','kvantGram · Appearance'),('AyuPreferences','kvantGram · Ghost & History')]:
            if path.parent.name=='values-ru':
                label={'Preferences':'kvantGram · Оформление','AyuPreferences':'kvantGram · Призрак и история'}.get(key,label)
            label=escape(label.replace('\\','\\\\').replace("'","\\'").replace('"','\\"'))
            text=re.sub(rf'(<string name="{key}"[^>]*>).*?(</string>)',lambda m:m[1]+label+m[2],text,flags=re.S)
        path.write_text(text,encoding='utf-8')
    for path in (root/'TMessagesProj/config').rglob('AndroidManifest*.xml'):
        text=path.read_text(encoding='utf-8')
        text=re.sub(r'android:label="[^"]*"','android:label="@string/AppName"',text)
        path.write_text(text,encoding='utf-8')
    locale=java/'org/telegram/messenger/LocaleController.java'
    edit(locale,'    private String getStringInternal(String key, String fallback, int res) {',
         '''    private String getStringInternal(String key, String fallback, int res) {
        if ("Preferences".equals(key) || "AyuPreferences".equals(key)) {
            return ApplicationLoader.applicationContext.getString(res);
        }''')
    constants=java/'com/radolyn/ayugram/AyuConstants.java'
    edit(constants,'public static String APP_NAME = "AyuGram";','public static String APP_NAME = '+json.dumps(name,ensure_ascii=False)+';')
    edit(constants,'public static String APP_GITHUB = "AyuGram/AyuGram4A";','public static String APP_GITHUB = "filat0vvv/kvant";')
    # Upstream OTA belongs to a different package and signing identity.
    updater=java/'com/exteragram/messenger/utils/UpdaterUtils.java'
    edit(updater,'if (BuildVars.PM_BUILD || checkingForUpdates || id != 1L ||',
         'if (true /* kvantGram: upstream APK is not our update */ || BuildVars.PM_BUILD || checkingForUpdates || id != 1L ||')
    edit(java/'com/exteragram/messenger/ExteraConfig.java',
         'checkUpdatesOnLaunch = preferences.getBoolean("checkUpdatesOnLaunch", true);','checkUpdatesOnLaunch = false;')
    edit(java/'com/exteragram/messenger/ExteraConfig.java',
         'centerTitle = preferences.getBoolean("centerTitle", false);','centerTitle = preferences.getBoolean("centerTitle", true);')
    # Keep history entirely local by default; AyuSync remains opt-in, as upstream.
    for source in (REPO/'android').rglob('*.java'):
        target=java/source.relative_to(REPO/'android')
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(source,target)
    for path in [java/'com/radolyn/ayugram/ui/AyuMessageHistory.java',
                 java/'com/radolyn/ayugram/messages/AyuMessagesController.java']:
        text=path.read_text(encoding='utf-8').replace('com.radolyn.ayugram.proprietary.AyuMessageUtils','com.kvant.messenger.history.AyuMessageUtils')
        path.write_text(text,encoding='utf-8')
    chat=java/'org/telegram/ui/ChatActivity.java'
    edit(chat,'com.radolyn.ayugram.proprietary.AyuHistoryHook','com.kvant.messenger.history.AyuHistoryHook')
    edit(chat,'endId, dialogId, limit, topicId, isSecretChat());','endId, dialogId, limit, topicId, isSecretChat(), load_type == 1);')
    # A chat containing only saved deletions must also open from the local history,
    # including when the ordinary Telegram cache is empty and there is no network.
    edit(chat,'''                    if (isCache) {
                        startId = minVal;
                        endId = minVal;
                    }''', '''                    if (isCache) {
                        startId = minVal;
                        endId = messages.isEmpty() ? maxVal : minVal;
                    } else if (messages.isEmpty()) {
                        endId = maxVal;
                    }''')
    edit(java/'com/radolyn/ayugram/AyuUtils.java',
         'for (int i = messages.size() - 1; i > 0; i--) {',
         'for (int i = messages.size() - 1; i >= 0; i--) {')
    # New nullable snapshot column, with an additive migration; never wipe local history.
    base=java/'com/radolyn/ayugram/database/entities/AyuMessageBase.java'
    edit(base,'public abstract class AyuMessageBase {','public abstract class AyuMessageBase {\n    public byte[] serializedMessage;')
    edit(java/'com/radolyn/ayugram/database/AyuDatabase.java','}, version = 21)','}, version = 22)')
    edit(java/'com/radolyn/ayugram/database/AyuData.java','.fallbackToDestructiveMigration()',
         '.addMigrations(new com.kvant.messenger.history.HistoryMigration())')
    edit(java/'com/radolyn/ayugram/database/dao/DeletedMessageDao.java','ORDER BY messageId LIMIT :limit','ORDER BY date DESC, messageId DESC LIMIT :limit')
    revisions=java/'com/radolyn/ayugram/database/dao/EditedMessageDao.java'
    edit(revisions,'ORDER BY entityCreateDate")','ORDER BY entityCreateDate, fakeId")')
    edit(revisions,'ORDER BY entityCreateDate DESC LIMIT 1','ORDER BY entityCreateDate DESC, fakeId DESC LIMIT 1')
    controller=java/'com/radolyn/ayugram/messages/AyuMessagesController.java'
    edit(controller,'public static final String attachmentsSubfolder = "Saved Attachments";',
         'public static final String attachmentsSubfolder = "kvant-history";')
    edit(controller,'''    public static final File attachmentsPath = new File(
            new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), AyuConstants.APP_NAME),
            attachmentsSubfolder
    );''', '''    public static final File attachmentsPath = new File(
            org.telegram.messenger.ApplicationLoader.applicationContext.getFilesDir(), attachmentsSubfolder);''')
    for method in ['public static AyuMessagesController getInstance()',
                   'public void onMessageDeleted(AyuSavePreferences prefs)',
                   'public void onMessageEdited(AyuSavePreferences prefs, TLRPC.Message newMessage)',
                   'public void onMessageEditedForce(AyuSavePreferences prefs)']:
        edit(controller,method,method.replace('public static ','public static synchronized ').replace('public void ','public synchronized void '))
    # Avoid crashing the edits viewer if its only revision was removed while opening it.
    edit(java/'com/radolyn/ayugram/ui/AyuMessageHistory.java','        var firstMsg = messages.get(0);',
         '        if (messages.isEmpty()) { fragmentView = new FrameLayout(context); return fragmentView; }\n        var firstMsg = messages.get(0);')
    edit(java/'org/telegram/messenger/FileLoader.java',
         '    public File getPathToMessage(TLRPC.Message message, boolean useFileDatabaseQueue) {',
         '''    public File getPathToMessage(TLRPC.Message message, boolean useFileDatabaseQueue) {
        File history = com.kvant.messenger.history.AyuMessageUtils.getSavedAttachment(message);
        if (history != null) return history;''')
    edit(java/'org/telegram/messenger/MessageObject.java',
         '''    public void checkMediaExistance(boolean useFileDatabaseQueue) {
        attachPathExists = false;
        mediaExists = false;''',
         '''    public void checkMediaExistance(boolean useFileDatabaseQueue) {
        attachPathExists = false;
        mediaExists = false;
        if (com.kvant.messenger.history.AyuMessageUtils.getSavedAttachment(messageOwner) != null) {
            attachPathExists = true; mediaExists = true; return;
        }''')
    edit(java/'org/telegram/ui/Cells/ChatMessageCell.java',
         '''                    } else if (messageObject.type == MessageObject.TYPE_PHOTO) {
                        if (messageObject.useCustomPhoto) {''',
         '''                    } else if (messageObject.type == MessageObject.TYPE_PHOTO) {
                        if (com.kvant.messenger.history.AyuMessageUtils.getSavedAttachment(messageObject.messageOwner) != null) {
                            photoImage.setImage(ImageLocation.getForPath(messageObject.messageOwner.attachPath), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, 0, null, currentMessageObject, 0);
                        } else if (messageObject.useCustomPhoto) {''')
    # Preserve the existing kvantGram proxy helper; it has no access to the history store.
    shutil.copyfile(REPO/'assets/AutoProxyManager.java',java/'org/telegram/messenger/AutoProxyManager.java')
    edit(java/'org/telegram/messenger/SharedConfig.java','proxyListLoaded = true;',
         'proxyListLoaded = true;\n            AutoProxyManager.start();')
    edit(java/'com/radolyn/ayugram/AyuConfig.java',
         'markReadAfterSend = preferences.getBoolean("markReadAfterSend", true);',
         'markReadAfterSend = preferences.getBoolean("markReadAfterSend", false);')
    connection=java/'org/telegram/tgnet/ConnectionsManager.java'
    edit(connection,'''if (!AyuConfig.sendUploadProgress &&
                    (
                            object instanceof TLRPC.TL_messages_setTyping ||
                            object instanceof TLRPC.TL_messages_setEncryptedTyping
                    )
            )''', 'if (com.kvant.messenger.history.GhostPacketPolicy.blocksTyping(object, AyuConfig.sendUploadProgress))')
    edit(connection,'''if (!AyuConfig.sendOnlinePackets && object instanceof TLRPC.TL_account_updateStatus) {
                var obj = ((TLRPC.TL_account_updateStatus) object);
                obj.offline = true;
            }''', 'com.kvant.messenger.history.GhostPacketPolicy.applyOnlinePolicy(object, AyuConfig.sendOnlinePackets);')
    edit(connection,'''if (
                    !AyuConfig.sendReadPackets &&
                            (
                                    object instanceof TLRPC.TL_messages_readHistory ||
                                    object instanceof TLRPC.TL_messages_readEncryptedHistory ||
                                    object instanceof TLRPC.TL_messages_readDiscussion ||
                                    object instanceof TLRPC.TL_messages_readMessageContents ||
                                    object instanceof TLRPC.TL_channels_readHistory ||
                                    object instanceof TLRPC.TL_channels_readMessageContents
                            )
            )''', 'if (com.kvant.messenger.history.GhostPacketPolicy.blocksRead(object, AyuConfig.sendReadPackets))')
    # Install the same brand icon under all launcher variants.
    image=Image.open(REPO/'assets/kvantgram-logo.png').convert('RGB')
    for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
        folder=res/f'mipmap-{density}';folder.mkdir(parents=True,exist_ok=True)
        for filename in ['ic_launcher.png','ic_launcher_round.png']+[p.name for p in folder.glob('icon_*_launcher*.png')]:
            image.resize((size,size),Image.Resampling.LANCZOS).save(folder/filename)
    folder=res/'drawable-nodpi';folder.mkdir(parents=True,exist_ok=True)
    image.save(folder/'kvant_logo.png')
    (res/'drawable/kvant_foreground.xml').write_text('''<inset xmlns:android="http://schemas.android.com/apk/res/android" android:inset="14%"><bitmap android:src="@drawable/kvant_logo" android:gravity="fill" /></inset>''',encoding='utf-8')
    (res/'values/kvant_brand.xml').write_text('<resources><color name="kvant_icon_background">#001139</color></resources>',encoding='utf-8')
    folder=res/'mipmap-anydpi-v26';folder.mkdir(parents=True,exist_ok=True)
    icon='''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/kvant_icon_background"/><foreground android:drawable="@drawable/kvant_foreground"/></adaptive-icon>'''
    for file in [folder/'ic_launcher.xml',folder/'ic_launcher_round.xml']+list(folder.glob('icon_*_launcher*.xml')):
        file.write_text(icon,encoding='utf-8')
    tests=root/'TMessagesProj/src/test/java'
    for source in (REPO/'android-tests').rglob('*.java'):
        target=tests/source.relative_to(REPO/'android-tests');target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
    fixture=root/'TMessagesProj/src/test/resources/ayu-schema-21.json'
    fixture.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(root/'TMessagesProj/schemas/com.radolyn.ayugram.database.AyuDatabase/21.json',fixture)
    print(f'AyuGram branding and local history applied: {name}, {package}')


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('source',type=Path)
    apply(parser.parse_args().source)

