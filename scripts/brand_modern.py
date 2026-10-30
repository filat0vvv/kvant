#!/usr/bin/env python3
"""kvantGram branding and non-destructive upgrade of pinned Telegram 13.1.1 sources."""
from pathlib import Path
from xml.sax.saxutils import escape
import html
import json
import re
import shutil
from PIL import Image
from brand_ayu import edit

REPO=Path(__file__).resolve().parents[1]
PACKAGE='com.kvant.messenger.ayu'  # Retain the installed package and its private data.
CHANNEL='kvantgram_official'
GITHUB='https://github.com/filat0vvv/kvant'
BRANDS=re.compile(r'AyuSync|AyuGram|[Ee]xtera[Gg]ram|Nagram\s*X\s*Turbo|NagramXTurbo|Nagram\s*X|NagramX|Nekogram|NekoX|Nagram')


def apply(root):
    java=root/'TMessagesProj/src/main/java'
    res=root/'TMessagesProj/src/main/res'
    gradle=root/'TMessagesProj/build.gradle'
    edit(root/'gradle.properties','APP_PACKAGE=nu.gpu.nagramxturbo','APP_PACKAGE='+PACKAGE)
    edit(gradle,'def verCode = 1261','def verCode = 40000')
    edit(gradle,"storeFile project.file('nagramxturbo.jks')","storeFile file(System.getenv('KVANT_KEYSTORE_PATH'))")
    for plugin in ['com.google.gms.google-services','com.google.firebase.crashlytics']:
        edit(gradle,"    id '"+plugin+"'",'    // kvantGram: no upstream Firebase project')
    edit(gradle,"String gramName = isTurboBase ? 'NagramXTurbo_base' : 'NagramXTurbo'","String gramName = 'kvantGram'")
    edit(java/'org/telegram/messenger/ApplicationLoader.java',
         '        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(AndroidUtil.shouldEnableCrashlytics());',
         '        com.kvant.messenger.history.SettingsUpgrade.migrate(applicationContext);')
    edit(java/'org/telegram/messenger/GooglePushListenerServiceProvider.java',
         '        return hasServices;', '        return false; // No Firebase project configured for this fork.')
    edit(java/'org/telegram/messenger/BuildVars.java',
         'public static boolean SUPPORTS_PASSKEYS = Build.VERSION.SDK_INT >= 34;',
         'public static boolean SUPPORTS_PASSKEYS = false;')
    edit(java/'tw/nekomimi/nekogram/NekoConfig.java',
         'addConfig("customSavePath", configTypeString, "Nagram")',
         'addConfig("customSavePath", configTypeString, "kvantGram")')
    edit(java/'tw/nekomimi/nekogram/NekoConfig.java',
         'addConfig("markReadAfterSend", configTypeBool, true)',
         'addConfig("markReadAfterSend", configTypeBool, false)')
    edit(java/'tw/nekomimi/nekogram/NekoConfig.java',
         'addConfig("showGhostInDrawer", configTypeBool, false)',
         'addConfig("showGhostInDrawer", configTypeBool, true)')
    for field in ['enableSaveDeletedMessages','enableSaveEditsHistory']:
        path=root/'TMessagesProj/src/main/kotlin/xyz/nextalone/nagram/NaConfig.kt'
        text=path.read_text(encoding='utf-8')
        text,count=re.subn(r'(val '+field+r'\s*=\s*addConfig\(\s*"[^"]+",\s*ConfigItem.configTypeBool,\s*)false',r'\g<1>true',text)
        if count != 1: raise RuntimeError('Changed history default: '+field)
        path.write_text(text,encoding='utf-8')
    # The upstream updater uses a different package, key and metadata channel.
    edit(java/'tw/nekomimi/nekogram/helpers/remote/UpdateHelper.java',
         '''        this.updateAlways = updateAlways;
        load(delegate);''', '''        if (delegate != null) delegate.onTLResponse(null, null);''')
    # Public-facing links: one official channel, own source, no chat/support rows.
    about=java/'tw/nekomimi/nekogram/settings/NekoAboutActivity.java'
    text=about.read_text(encoding='utf-8').replace('"nagramxturbo"','"'+CHANNEL+'"').replace('@nagramxturbo','@'+CHANNEL)
    text=text.replace('https://github.com/temporaryna/NagramXTurbo',GITHUB).replace('R.drawable.turbo_logo','R.drawable.kvant_mark')
    about.write_text(text,encoding='utf-8')
    edit(java/'tw/nekomimi/nekogram/settings/NekoChatSettingsActivity.java','"Nekogram"','"kvantGram"')
    for path in [java/'com/radolyn/ayugram/database/AyuData.java',java/'tw/nekomimi/nekogram/settings/TurboSettingsActivity.java']:
        path.write_text(path.read_text(encoding='utf-8').replace('Ayu database','kvantGram history database'),encoding='utf-8')
    # Keep ghost mode directly accessible from the main custom settings page.
    settings=java/'tw/nekomimi/nekogram/settings/NekoSettingsActivity.java'
    edit(settings,'    private int generalRow;','    private int kvantGhostRow;\n    private int generalRow;')
    edit(settings,'        menu.addItem(MENU_SYNC, R.drawable.cloud_sync, resourcesProvider);',
         '        // KvantSync uses local files until an own sync backend is configured.')
    edit(java/'tw/nekomimi/nekogram/helpers/CloudSettingsHelper.java',
         '    public void doAutoSync() {\n        if (!autoSync)',
         '    public void doAutoSync() {\n        if (true) return; // Upstream bot storage is not our service.\n        if (!autoSync)')
    edit(settings,'        generalRow = addRow();','        kvantGhostRow = addRow();\n        generalRow = addRow();')
    edit(settings,'\n        if (position == chatRow) {','''
        if (position == kvantGhostRow) {
            presentFragment(new GhostModeActivity());
        } else if (position == chatRow) {''')
    edit(settings,'                    if (position == chatRow) {','''                    if (position == kvantGhostRow) {
                        textCell.setTextAndIcon(getString(R.string.GhostMode), R.drawable.msg_permissions, true);
                    } else if (position == chatRow) {''')
    edit(settings,'} else if (position == chatRow || position == generalRow',
         '} else if (position == kvantGhostRow || position == chatRow || position == generalRow')
    edit(java/'com/radolyn/ayugram/AyuConstants.java',
         'AYU_DATABASE_EXPORT = AYU_DATABASE + ".db"','AYU_DATABASE_EXPORT = "kvantgram-history.db"')
    # Brand resource text while keeping resource keys and original copyright headers.
    overrides={
        'AppName':['kvantGram','kvantGram'],'AppNameBeta':['kvantGram','kvantGram'],
        'NekoSettings':['kvantGram settings','Настройки kvantGram'],
        'NagramX':['kvantGram','kvantGram'],'NagramXTurbo':['History & tools \u00b7 kvantGram','\u0418\u0441\u0442\u043e\u0440\u0438\u044f \u0438 \u0438\u043d\u0441\u0442\u0440\u0443\u043c\u0435\u043d\u0442\u044b kvantGram'],
        'TurboChannel':['kvantGram channel','Канал kvantGram'],
        'XChannel':['kvantGram channel','Канал kvantGram'],
        'BackupSettings':['KvantSync · settings backup','KvantSync · копия настроек'],
        'ImportSettings':['KvantSync · restore settings','KvantSync · восстановление'],
        'ClearMessageDatabase':['Clear kvantGram history','Очистить историю kvantGram'],
        'ClearMessageDatabaseNotification':['kvantGram history cleared','История kvantGram очищена'],
        'ExportAyuDB':['Export kvantGram history','Экспорт истории kvantGram'],
        'ImportAyuDB':['Import kvantGram history','Импорт истории kvantGram'],
        'GhostMode':['Ghost mode · kvantGram','Призрак · kvantGram'],
    }
    records={}
    pattern=re.compile(r'(<string\s+name="([^"]+)"[^>]*>)(.*?)(</string>)',re.S)
    for path in res.glob('values*/*.xml'):
        lang='ru' if path.parent.name.startswith('values-ru') else 'en'
        def replace(m):
            key,body=m[2],m[3]
            if key in overrides:
                body=escape(overrides[key][lang=='ru'])
            elif BRANDS.search(body):
                body=BRANDS.sub(lambda x:'KvantSync' if x[0]=='AyuSync' else 'kvantGram',body)
            if path.parent.name in ['values','values-ru','values-ru-rRU']:
                records.setdefault(key,{})[lang]=(html.unescape(body).replace("\\'","'").replace('\\"','"')
                    .replace('\\n','\n').replace('\\t','\t').replace('<![CDATA[','').replace(']]>',''))
            return m[1]+body+m[4]
        path.write_text(pattern.sub(replace,path.read_text(encoding='utf-8')),encoding='utf-8')
    # Cloud language packs must not restore upstream product names on these keys.
    own={k:v for k,v in records.items() if k in overrides or 'kvantGram' in v.get('en','') or 'KvantSync' in v.get('en','')}
    dest=java/'com/kvant/messenger/branding';dest.mkdir(parents=True,exist_ok=True)
    code='''package com.kvant.messenger.branding;
import org.telegram.messenger.R;
public final class KvantBranding {
  private static final java.util.Map<String,String[]> VALUES=new java.util.HashMap<>();
  private static final java.util.Map<Integer,String> KEYS=new java.util.HashMap<>();
  static {
'''
    for key,value in sorted(own.items()):
        if not re.fullmatch(r'[A-Za-z_][A-Za-z_0-9]*',key): raise ValueError(key)
        en=value.get('en',value.get('ru',''));ru=value.get('ru',en)
        code+=f'    VALUES.put({json.dumps(key)},new String[]{{{json.dumps(en,ensure_ascii=False)},{json.dumps(ru,ensure_ascii=False)}}}); KEYS.put(R.string.{key},{json.dumps(key)});\n'
    code+='''  }
  public static String get(String key,int res,String language) {
    if(key==null) key=KEYS.get(res);
    String[] values=VALUES.get(key);
    return values==null?null:values[language!=null&&language.startsWith("ru")?1:0];
  }
}
'''
    (dest/'KvantBranding.java').write_text(code,encoding='utf-8')
    locale=java/'org/telegram/messenger/LocaleController.java'
    edit(locale,'    private String getStringV2(String key, @StringRes int stringRes, String fallback) {',
         '''    private String getStringV2(String key, @StringRes int stringRes, String fallback) {
        String brand = com.kvant.messenger.branding.KvantBranding.get(key, stringRes,
                currentLocaleInfo == null ? java.util.Locale.getDefault().getLanguage() : currentLocaleInfo.shortName);
        if (brand != null) return brand;''')
    # Preserve the old Room v22 snapshot field and index variant through the upgrade.
    edit(java/'com/radolyn/ayugram/database/entities/AyuMessageBase.java',
         'public abstract class AyuMessageBase {','public abstract class AyuMessageBase {\n    public byte[] serializedMessage;')
    edit(java/'com/radolyn/ayugram/database/AyuDatabase.java','VERSION = 26','VERSION = 27')
    edit(java/'com/radolyn/ayugram/database/AyuData.java','.fallbackToDestructiveMigrationOnDowngrade()','// Keep history on unsupported downgrade; never erase it automatically.')
    edit(java/'com/radolyn/ayugram/database/AyuData.java','MIGRATION_24_25, MIGRATION_25_26)',
         'MIGRATION_24_25, MIGRATION_25_26, new com.kvant.messenger.history.HistoryUpgrade())')
    helper=java/'com/radolyn/ayugram/proprietary/AyuMessageUtils.java'
    edit(helper,'    public static void map(AyuMessageBase source, TLRPC.Message target, int accountId) {',
         '''    public static void map(AyuMessageBase source, TLRPC.Message target, int accountId) {
        if (com.kvant.messenger.history.LegacySnapshots.restore(source, target)) return;''')
    edit(helper,'    public static void mapMedia(AyuMessageBase base, TLRPC.Message target, int accountId) {',
         '''    public static void mapMedia(AyuMessageBase base, TLRPC.Message target, int accountId) {
        if (com.kvant.messenger.history.LegacySnapshots.restoreMedia(base, target)) return;''')
    edit(java/'com/radolyn/ayugram/messages/AyuMessagesController.java',
         'new File(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), AyuConstants.APP_NAME), attachmentsSubfolder)',
         'new File(org.telegram.messenger.ApplicationLoader.applicationContext.getFilesDir(), "kvant-history")')
    edits=java/'com/radolyn/ayugram/database/dao/EditedMessageDao.java'
    edit(edits,'ORDER BY entityCreateDate")','ORDER BY entityCreateDate, fakeId")')
    edit(edits,'ORDER BY entityCreateDate DESC LIMIT 1','ORDER BY entityCreateDate DESC, fakeId DESC LIMIT 1')
    loader=java/'org/telegram/messenger/FileLoader.java'
    shutil.copyfile(REPO/'assets/AutoProxyManager.java',java/'org/telegram/messenger/AutoProxyManager.java')
    proxy=java/'org/telegram/messenger/AutoProxyManager.java'
    edit(proxy,'import org.telegram.tgnet.ConnectionsManager;',
         'import org.telegram.tgnet.ConnectionsManager;\nimport org.telegram.utils.proxy.ProxySettings;')
    edit(proxy,'new SharedConfig.ProxyInfo(host, port, "", "", secret)',
         'new SharedConfig.ProxyInfo(ProxySettings.builder().setType(ProxySettings.Type.MTPROTO).setAddress(host).setPort(port).setSecret(secret).build())')
    edit(proxy,'new SharedConfig.ProxyInfo(s, Integer.parseInt(p), "", "", sec)',
         'new SharedConfig.ProxyInfo(ProxySettings.builder().setType(ProxySettings.Type.MTPROTO).setAddress(s).setPort(Integer.parseInt(p)).setSecret(sec).build())')
    edit(proxy,'checkProxy(p.address, p.port, "", "", p.secret, time ->','checkProxy(p.settings, time ->')
    text=proxy.read_text(encoding='utf-8').replace('p.address','p.settings.getAddress()').replace('p.port','p.settings.getPort()').replace('p.secret','p.settings.getSecret()')
    text=text.replace('ConnectionsManager.setProxySettings(true, p.settings.getAddress(), p.settings.getPort(), "", "", p.settings.getSecret());',
                      'ConnectionsManager.setProxySettings(true, p.settings);')
    text=text.replace('prefs.edit()\n                                .putBoolean',
                      'SharedPreferences.Editor editor = prefs.edit();\n                            p.settings.toSharedPreferences(editor);\n                            editor.putBoolean')
    proxy.write_text(text,encoding='utf-8')
    edit(java/'org/telegram/messenger/SharedConfig.java','proxyListLoaded = true;',
         'proxyListLoaded = true;\n        AutoProxyManager.start();')
    edit(loader,'    public File getPathToMessage(TLRPC.Message message, boolean forceCache, boolean useFileDatabaseQueue) {',
         '''    public File getPathToMessage(TLRPC.Message message, boolean forceCache, boolean useFileDatabaseQueue) {
        File legacy = com.kvant.messenger.history.LegacySnapshots.getSavedAttachment(message);
        if (legacy != null) return legacy;''')
    # Reuse the already-reviewed own animation, keeping the unconsumed launch Intent.
    shutil.copyfile(REPO/'android/com/kvant/messenger/branding/KvantStartup.java',dest/'KvantStartup.java')
    launch=java/'org/telegram/ui/LaunchActivity.java'
    edit(launch,'    protected void onCreate(Bundle savedInstanceState) {','''    protected void onCreate(Bundle savedInstanceState) {
        final Intent kvantStartupIntent = getIntent() == null ? null : new Intent(getIntent());''')
    edit(launch,'        RestrictedLanguagesSelectActivity.checkRestrictedLanguages(false);',
         '''        RestrictedLanguagesSelectActivity.checkRestrictedLanguages(false);
        com.kvant.messenger.branding.KvantStartup.show(this, frameLayout, savedInstanceState, kvantStartupIntent);''')
    intro=java/'org/telegram/ui/IntroActivity.java'
    edit(intro,'        titles[0] = ssb;','        titles[0] = "kvantGram";')
    text=intro.read_text(encoding='utf-8').replace('R.drawable.telegram_logo','R.drawable.kvant_mark')
    text,count=re.subn(r'        TextureView textureView = new TextureView\(context\);.*?(?=        viewPager = new ViewPager\(context\);)',
        '''        android.widget.ImageView brandLogo = new android.widget.ImageView(context);
        brandLogo.setImageResource(R.drawable.kvant_mark);
        frameLayout2.addView(brandLogo, LayoutHelper.createFrame(ICON_WIDTH_DP, ICON_HEIGHT_DP, Gravity.CENTER));

''',text,flags=re.S)
    if count!=1: raise RuntimeError('Changed intro layout')
    intro.write_text(text,encoding='utf-8')
    image=Image.open(REPO/'assets/kvantgram-logo.png').convert('RGB')
    nodpi=res/'drawable-nodpi';nodpi.mkdir(exist_ok=True)
    image.save(nodpi/'kvant_logo.png');shutil.copyfile(REPO/'assets/kvantgram-mark.png',nodpi/'kvant_mark.png')
    for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
        folder=res/('mipmap-'+density);folder.mkdir(exist_ok=True)
        for path in folder.iterdir():
            if ('launcher' in path.name) and path.suffix in ['.png','.webp']:
                image.resize((size,size),Image.Resampling.LANCZOS).save(path)
    (res/'values/kvant_brand.xml').write_text('<resources><color name="kvant_start_background">#001139</color><color name="kvant_icon_background">#0c0d10</color></resources>')
    (res/'drawable/kvant_foreground.xml').write_text('<inset xmlns:android="http://schemas.android.com/apk/res/android" android:inset="14%"><bitmap android:src="@drawable/kvant_logo" android:gravity="fill"/></inset>')
    (res/'drawable/kvant_splash_icon.xml').write_text('<inset xmlns:android="http://schemas.android.com/apk/res/android" android:inset="20%"><bitmap android:src="@drawable/kvant_mark" android:gravity="fill"/></inset>')
    (res/'drawable/kvant_start_background.xml').write_text('<layer-list xmlns:android="http://schemas.android.com/apk/res/android"><item android:drawable="@color/kvant_start_background"/><item android:width="112dp" android:height="112dp" android:gravity="center"><bitmap android:src="@drawable/kvant_mark" android:gravity="fill"/></item></layer-list>')
    for path in res.glob('mipmap-anydpi*/*launcher*.xml'):
        path.write_text('<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/kvant_icon_background"/><foreground android:drawable="@drawable/kvant_foreground"/></adaptive-icon>')
    for path in res.glob('values*/styles.xml'):
        text=path.read_text(encoding='utf-8')
        text=re.sub(r'<!--\s*(<item name="android:windowSplashScreenAnimatedIcon">.*?</item>)\s*-->',r'\1',text)
        text=re.sub(r'(<item name="android:windowSplashScreenAnimatedIcon">).*?(</item>)',r'\g<1>@drawable/kvant_splash_icon\2',text)
        text=re.sub(r'(<item name="android:windowSplashScreenBackground">).*?(</item>)',r'\g<1>@color/kvant_start_background\2',text)
        def start_theme(m):
            block=m[0].replace('<item name="android:colorBackground">@android:color/white</item>',
                              '<item name="android:colorBackground">@color/kvant_start_background</item>')
            return block.replace('<item name="android:windowBackground">@android:color/white</item>',
                                 '<item name="android:windowBackground">@drawable/kvant_start_background</item>')
        text=re.sub(r'<style name="Theme.TMessages.Start"[^>]*>.*?</style>',start_theme,text,flags=re.S)
        path.write_text(text,encoding='utf-8')
    for path in root.glob('TMessagesProj/**/AndroidManifest*.xml'):
        text=path.read_text(encoding='utf-8');text=re.sub(r'android:label="[^"]*"','android:label="@string/AppName"',text)
        path.write_text(text,encoding='utf-8')
    for source in (REPO/'modern/android').rglob('*.java'):
        target=java/source.relative_to(REPO/'modern/android');target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
    tests=root/'TMessagesProj/src/test/java'
    for source in (REPO/'modern/tests').rglob('*.java'):
        target=tests/source.relative_to(REPO/'modern/tests');target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
    fixture=root/'TMessagesProj/src/test/resources/kvant-schema-21.json';fixture.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(root/'TMessagesProj/schemas/com.radolyn.ayugram.database.AyuDatabase/21.json',fixture)
    with gradle.open('a',encoding='utf-8') as f:
        f.write("\ndependencies { testImplementation 'junit:junit:4.13.2'; testImplementation 'org.robolectric:robolectric:4.17' }\nandroid.testBuildType = 'release'\nandroid.testOptions.unitTests.includeAndroidResources = true\nandroid.testOptions.unitTests.all { maxHeapSize = '2g' }\n")
    print('Applied kvantGram 13.1.1 branding, own channel and additive history upgrade')


if __name__=='__main__':
    import sys
    apply(Path(sys.argv[1]))
