package com.kvant.messenger.history;

import com.radolyn.ayugram.database.entities.EditedMessage;
import org.junit.Test;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.legacy.TL_legacy_message;
import static org.junit.Assert.*;

public class LegacySnapshotsTest {
    @Test public void oldTelegram169SnapshotRetainsTextFormattingAndReply() {
        TL_legacy_message.TL_message_layer169 old=new TL_legacy_message.TL_message_layer169() {
            @Override protected void writeAttachPath(org.telegram.tgnet.OutputSerializedData stream) {}
        };
        old.id=42;old.date=1000;old.message="old history";
        old.flags=256|128|8;
        old.peer_id=new TLRPC.TL_peerUser();old.peer_id.user_id=100;
        old.from_id=new TLRPC.TL_peerUser();old.from_id.user_id=200;
        TLRPC.TL_messageEntityBold bold=new TLRPC.TL_messageEntityBold();bold.offset=0;bold.length=3;old.entities.add(bold);
        old.reply_to=new TLRPC.TL_messageReplyHeader_layer165();old.reply_to.reply_to_msg_id=40;
        SerializedData data=new SerializedData();old.serializeToStream(data);
        EditedMessage row=new EditedMessage();row.serializedMessage=data.toByteArray();data.cleanup();
        row.messageId=42;row.dialogId=100;row.mediaPath="/private/kvant-history/voice.ogg";
        TLRPC.TL_message restored=new TLRPC.TL_message();restored.ayuDeleted=true;
        assertTrue(LegacySnapshots.restore(row,restored));
        assertEquals("old history",restored.message);
        assertEquals(40,restored.reply_to.reply_to_msg_id);
        assertEquals(1,restored.entities.size());assertEquals(3,restored.entities.get(0).length);
        assertTrue(restored.ayuDeleted);assertFalse(restored.unread);
        assertEquals(row.mediaPath,restored.attachPath);
    }
}
