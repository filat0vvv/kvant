// SPDX-License-Identifier: GPL-2.0-or-later
package com.kvant.messenger.history;

import android.util.Pair;
import android.util.SparseArray;
import com.radolyn.ayugram.AyuConfig;
import com.radolyn.ayugram.database.entities.DeletedMessageFull;
import com.radolyn.ayugram.messages.AyuMessagesController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Merge local deleted history into a loaded page without removing any live message. */
public final class AyuHistoryHook {
    private AyuHistoryHook() {}

    public static Pair<Integer,Integer> getMinAndMaxIds(ArrayList<MessageObject> messages) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (MessageObject message : messages) {
            if (message == null || message.getId() == 0) continue;
            min = Math.min(min, message.getId()); max = Math.max(max, message.getId());
        }
        return min == Integer.MAX_VALUE ? new Pair<>(0,0) : new Pair<>(min,max);
    }

    public static void doHook(int account, ArrayList<MessageObject> page,
                              SparseArray<MessageObject>[] visible, int start, int end,
                              long dialogId, int limit, int topicId, boolean secret, boolean ascending) {
        if (!AyuConfig.saveDeletedMessages) return;
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId == 0) return;
        List<DeletedMessageFull> saved = AyuMessagesController.getInstance().getMessages(
                userId, dialogId, topicId, Math.min(start,end), Math.max(start,end), limit);
        HashSet<Integer> present = new HashSet<>();
        for (MessageObject message : page) {
            if (message.messageOwner.dialog_id == dialogId) present.add(message.getId());
        }
        for (DeletedMessageFull record : saved) {
            if (record.message.userId != userId || record.message.dialogId != dialogId
                    || record.message.topicId != topicId) continue;
            if (present.contains(record.message.messageId)) {
                for (MessageObject message : page) {
                    if (message.getId() == record.message.messageId && message.messageOwner.dialog_id == dialogId)
                        message.messageOwner.ayuDeleted = true;
                }
                continue;
            }
            boolean alreadyVisible = false;
            for (SparseArray<MessageObject> dictionary : visible) {
                MessageObject existing = dictionary.get(record.message.messageId);
                if (existing != null && existing.messageOwner.dialog_id == dialogId) {
                    existing.messageOwner.ayuDeleted = true;
                    alreadyVisible = true; break;
                }
            }
            if (alreadyVisible) continue;
            try {
                TLRPC.Message restored = AyuMessageUtils.restore(record.message);
                restored.ayuDeleted = true;
                MessageObject object = new MessageObject(account, restored, false, true);
                page.add(object);
                present.add(restored.id);
            } catch (Exception e) { FileLog.e(e); }
        }
        Comparator<MessageObject> chronological = Comparator
                .comparingInt((MessageObject m) -> m.messageOwner.date).thenComparingInt(MessageObject::getId);
        page.sort(ascending ? chronological : chronological.reversed());
    }
}
