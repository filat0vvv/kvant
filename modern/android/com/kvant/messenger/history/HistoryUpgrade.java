// SPDX-License-Identifier: GPL-2.0-or-later
package com.kvant.messenger.history;

import android.database.Cursor;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** Additive upgrade for both upstream history and kvantGram's v22 snapshot variant. */
public final class HistoryUpgrade extends Migration {
    public HistoryUpgrade() { super(26, 27); }

    @Override public void migrate(SupportSQLiteDatabase db) {
        for (String table : new String[]{"EditedMessage", "DeletedMessage"}) {
            boolean exists = false;
            try (Cursor columns = db.query("PRAGMA table_info(`" + table + "`)")) {
                int name = columns.getColumnIndexOrThrow("name");
                while (columns.moveToNext()) if ("serializedMessage".equals(columns.getString(name))) exists = true;
            }
            if (!exists) db.execSQL("ALTER TABLE `" + table + "` ADD COLUMN `serializedMessage` BLOB");
        }
        // Original kvantGram v22 predates upstream v22's index-only migration.
        db.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_userId_dialogId_messageId ON deletedmessage(userId, dialogId, messageId)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_userId_dialogId_topicId_messageId ON deletedmessage(userId, dialogId, topicId, messageId)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_userId_dialogId_replyMessageId_messageId ON deletedmessage(userId, dialogId, replyMessageId, messageId)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_userId_dialogId_groupedId_messageId ON deletedmessage(userId, dialogId, groupedId, messageId)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_dialogId ON deletedmessage(dialogId)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_editedmessage_userId_dialogId_messageId_entityCreateDate ON editedmessage(userId, dialogId, messageId, entityCreateDate)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_editedmessage_userId_entityCreateDate ON editedmessage(userId, entityCreateDate)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_editedmessage_dialogId_messageId ON editedmessage(dialogId, messageId)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessagereaction_deletedMessageId ON deletedmessagereaction(deletedMessageId)");
    }
}
