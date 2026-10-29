// SPDX-License-Identifier: GPL-2.0-or-later
package com.kvant.messenger.history;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/** Decisions used at AyuGram's existing outgoing request boundary. */
public final class GhostPacketPolicy {
    private GhostPacketPolicy() {}
    public static boolean blocksTyping(TLObject request, boolean sendProgress) {
        return !sendProgress && (request instanceof TLRPC.TL_messages_setTyping
                || request instanceof TLRPC.TL_messages_setEncryptedTyping);
    }
    public static boolean blocksRead(TLObject request, boolean sendRead) {
        return !sendRead && (request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_messages_readEncryptedHistory
                || request instanceof TLRPC.TL_messages_readDiscussion
                || request instanceof TLRPC.TL_messages_readMessageContents
                || request instanceof TLRPC.TL_channels_readHistory
                || request instanceof TLRPC.TL_channels_readMessageContents);
    }
    public static void applyOnlinePolicy(TLObject request, boolean sendOnline) {
        if (!sendOnline && request instanceof TLRPC.TL_account_updateStatus)
            ((TLRPC.TL_account_updateStatus) request).offline = true;
    }
}
