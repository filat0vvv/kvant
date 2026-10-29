// SPDX-License-Identifier: GPL-2.0-or-later
package com.kvant.messenger.history;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import com.radolyn.ayugram.database.AyuDatabase;
import com.radolyn.ayugram.database.entities.DeletedMessage;
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
import org.telegram.tgnet.TLRPC;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class, sdk=28)
public class HistoryRegressionTest {
    private Context context;
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication();
        ApplicationLoader.applicationContext=context;
    }
    private TLRPC.TL_message message(String text) {
        TLRPC.TL_message m=new TLRPC.TL_message();
        m.id=42; m.date=1000; m.message=text; m.dialog_id=100;
        m.peer_id=new TLRPC.TL_peerUser();m.peer_id.user_id=100;
        m.from_id=new TLRPC.TL_peerUser();m.from_id.user_id=200;
        m.flags=256|128|8;
        TLRPC.TL_messageEntityBold bold=new TLRPC.TL_messageEntityBold();
        bold.offset=0;bold.length=2;m.entities.add(bold);
        m.reply_to=new TLRPC.TL_messageReplyHeader();m.reply_to.reply_to_msg_id=40;
        return m;
    }
    private DeletedMessage capture(long user,long dialog,String text) {
        DeletedMessage row=new DeletedMessage();
        AyuMessageUtils.capture(message(text),row,user,dialog,0,42,2000,true,true);
        return row;
    }
    private AyuDatabase open(String name) {
        return Room.databaseBuilder(context,AyuDatabase.class,name)
                .allowMainThreadQueries().addMigrations(new HistoryMigration()).build();
    }
    @Test public void snapshotIsIndependentAndPreservesFormattingReply() {
        TLRPC.TL_message source=message("old text");
        EditedMessage row=new EditedMessage();
        AyuMessageUtils.capture(source,row,1,100,0,42,2000,true,true);
        source.message="new text";source.entities.clear();
        TLRPC.Message restored=AyuMessageUtils.restore(row);
        assertEquals("old text",restored.message);
        assertEquals(1,restored.entities.size());
        assertEquals(40,restored.reply_to.reply_to_msg_id);
        assertEquals(42,restored.id);assertEquals(100,restored.dialog_id);
        assertFalse(restored.unread);
    }
    @Test public void formattingPreferenceDoesNotModifyOriginal() {
        TLRPC.TL_message source=message("text");
        EditedMessage row=new EditedMessage();
        AyuMessageUtils.capture(source,row,1,100,0,42,2000,false,false);
        assertTrue(AyuMessageUtils.restore(row).entities.isEmpty());
        assertEquals(1,source.entities.size());
    }
    @Test public void historySurvivesReopenAndSeparatesAccountsAndDialogs() {
        String name="history-reopen.db";context.deleteDatabase(name);
        AyuDatabase db=open(name);
        db.deletedMessageDao().insert(capture(1,100,"account one"));
        db.deletedMessageDao().insert(capture(2,100,"account two"));
        db.deletedMessageDao().insert(capture(1,101,"another dialog"));
        db.close();db=open(name);
        assertEquals("account one",AyuMessageUtils.restore(db.deletedMessageDao().getMessage(1,100,42).message).message);
        assertEquals("account two",AyuMessageUtils.restore(db.deletedMessageDao().getMessage(2,100,42).message).message);
        assertEquals(1,db.deletedMessageDao().getMessages(1,100,0,0,100,500).size());
        assertNull(db.deletedMessageDao().getMessage(3,100,42));
        db.close();
    }
    @Test public void mediaCopySurvivesCacheDeletionAndMissingFilesStayMissing() throws Exception {
        File cached=new File(context.getCacheDir(),"voice.ogg");
        Files.write(cached.toPath(),"voice payload".getBytes(StandardCharsets.UTF_8));
        File destination=new File(context.getFilesDir(),"kvant-history/1/100/42");
        String saved=AyuMessageUtils.copyAvailable(cached,destination,"revision-media");
        assertTrue(cached.exists()); // Copy, never move an active Telegram cache entry.
        assertTrue(cached.delete());
        assertEquals("voice payload",new String(Files.readAllBytes(new File(saved).toPath()),StandardCharsets.UTF_8));
        assertNull(AyuMessageUtils.copyAvailable(cached,destination,"missing"));
    }
    @Test public void deletedHistoryPagesPreferNewestAndHonorTopic() {
        String name="history-page.db";context.deleteDatabase(name);AyuDatabase db=open(name);
        for(int i=1;i<=4;i++) {
            DeletedMessage row=capture(1,100,"message "+i);row.messageId=i;row.date=i;
            db.deletedMessageDao().insert(row);
        }
        DeletedMessage other=capture(1,100,"another topic");other.topicId=7;other.date=999;
        db.deletedMessageDao().insert(other);
        var page=db.deletedMessageDao().getMessages(1,100,0,0,100,2);
        assertEquals(2,page.size());assertEquals(4,page.get(0).message.messageId);assertEquals(3,page.get(1).message.messageId);
        db.close();
    }
    @Test public void editsInSameSecondKeepInsertionOrder() {
        String name="history-edits.db";context.deleteDatabase(name);AyuDatabase db=open(name);
        for(String text:new String[]{"first version","second version"}) {
            EditedMessage row=new EditedMessage();
            AyuMessageUtils.capture(message(text),row,1,100,0,42,2000,true,true);
            db.editedMessageDao().insert(row);
        }
        assertEquals("first version",db.editedMessageDao().getAllRevisions(1,100,42).get(0).text);
        assertEquals("second version",db.editedMessageDao().getLastRevision(1,100,42).text);
        db.close();
    }
    @Test public void migrationFrom21KeepsExistingHistory() throws Exception {
        String name="history-migration.db";context.deleteDatabase(name);
        File path=context.getDatabasePath(name);path.getParentFile().mkdirs();
        SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(path,null);
        String schema=new String(getClass().getResourceAsStream("/ayu-schema-21.json").readAllBytes(),StandardCharsets.UTF_8);
        JSONArray entities=new JSONObject(schema).getJSONObject("database").getJSONArray("entities");
        for(int i=0;i<entities.length();i++) {
            JSONObject entity=entities.getJSONObject(i);
            old.execSQL(entity.getString("createSql").replace("${TABLE_NAME}",entity.getString("tableName")));
        }
        // Create a complete old record with default values for every required primitive column.
        JSONObject entity=entities.getJSONObject(0);
        JSONArray fields=entity.getJSONArray("fields");
        android.content.ContentValues values=new android.content.ContentValues();
        for(int i=0;i<fields.length();i++) {
            JSONObject field=fields.getJSONObject(i);
            if(field.getBoolean("notNull"))values.put(field.getString("columnName"),0);
        }
        values.put("fakeId",1);values.put("userId",1);values.put("dialogId",100);values.put("messageId",42);
        values.put("text","history before migration");old.insertOrThrow("EditedMessage",null,values);
        old.setVersion(21);old.close();
        AyuDatabase db=open(name);
        assertEquals("history before migration",db.editedMessageDao().getAllRevisions(1,100,42).get(0).text);
        assertNull(db.editedMessageDao().getAllRevisions(1,100,42).get(0).serializedMessage);
        db.close();
    }
}
