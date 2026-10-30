package com.kvant.messenger.history;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.radolyn.ayugram.database.AyuData;
import com.radolyn.ayugram.database.AyuDatabase;
import com.radolyn.ayugram.database.entities.EditedMessage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.telegram.messenger.ApplicationLoader;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class,sdk=28)
public class HistoryUpgradeTest {
    private Context context;
    @Before public void setup() { context=RuntimeEnvironment.getApplication();ApplicationLoader.applicationContext=context; }

    private AyuDatabase open(String name) throws Exception {
        Method method=AyuData.class.getDeclaredMethod("createDatabase",String.class);
        method.setAccessible(true);
        return (AyuDatabase)method.invoke(null,name);
    }

    private void legacy(String name,int version) throws Exception {
        context.deleteDatabase(name);
        File path=context.getDatabasePath(name);path.getParentFile().mkdirs();
        SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(path,null);
        JSONObject schema=new JSONObject(new String(getClass().getResourceAsStream("/kvant-schema-21.json").readAllBytes(),StandardCharsets.UTF_8));
        JSONArray entities=schema.getJSONObject("database").getJSONArray("entities");
        for(int i=0;i<entities.length();i++) {
            JSONObject entity=entities.getJSONObject(i);String table=entity.getString("tableName");
            db.execSQL(entity.getString("createSql").replace("${TABLE_NAME}",table));
            if(version==22 && !table.equals("DeletedMessageReaction"))db.execSQL("ALTER TABLE `"+table+"` ADD COLUMN serializedMessage BLOB");
        }
        for(int entityIndex=0;entityIndex<2;entityIndex++) {
            JSONObject entity=entities.getJSONObject(entityIndex);
            for(int account=1;account<=2;account++) {
                ContentValues values=new ContentValues();JSONArray fields=entity.getJSONArray("fields");
                for(int i=0;i<fields.length();i++) {
                    JSONObject f=fields.getJSONObject(i);
                    if(f.getBoolean("notNull"))values.put(f.getString("columnName"),0);
                }
                values.put("fakeId",account);values.put("userId",account);values.put("dialogId",100);
                values.put("messageId",42);values.put("text","account "+account);values.put("mediaPath","/private/kvant-history/saved.ogg");
                if(version==22)values.put("serializedMessage",new byte[]{1,2,3,4,5});
                db.insertOrThrow(entity.getString("tableName"),null,values);
            }
        }
        db.setVersion(version);db.close();
    }

    @Test public void customV22KeepsSnapshotsAccountsAndMediaPaths() throws Exception {
        String name="kvant-v22.db";legacy(name,22);
        AyuDatabase db=open(name);
        EditedMessage row=db.editedMessageDao().getAllRevisions(1,100,42).get(0);
        assertEquals("account 1",row.text);
        assertArrayEquals(new byte[]{1,2,3,4,5},row.serializedMessage);
        assertEquals("/private/kvant-history/saved.ogg",row.mediaPath);
        assertEquals("account 2",db.deletedMessageDao().getMessage(2,100,42).message.text);
        assertNull(db.deletedMessageDao().getMessage(3,100,42));
        assertEquals(27,db.getOpenHelper().getWritableDatabase().getVersion());
        db.close();
        db=open(name);assertEquals("account 1",db.deletedMessageDao().getMessage(1,100,42).message.text);db.close();
    }

    @Test public void upstreamV21AlsoUpgradesWithoutDestructiveFallback() throws Exception {
        String name="kvant-v21.db";legacy(name,21);
        AyuDatabase db=open(name);
        assertEquals("account 1",db.editedMessageDao().getAllRevisions(1,100,42).get(0).text);
        assertNull(db.editedMessageDao().getAllRevisions(1,100,42).get(0).serializedMessage);
        assertEquals(27,db.getOpenHelper().getWritableDatabase().getVersion());db.close();
    }
}
