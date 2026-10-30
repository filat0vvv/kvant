// SPDX-License-Identifier: GPL-2.0-or-later
package com.kvant.messenger.history;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public final class HistoryMigration extends Migration {
    public HistoryMigration() { super(21,22); }
    @Override public void migrate(SupportSQLiteDatabase db) {
        db.execSQL("ALTER TABLE EditedMessage ADD COLUMN serializedMessage BLOB");
        db.execSQL("ALTER TABLE DeletedMessage ADD COLUMN serializedMessage BLOB");
    }
}
