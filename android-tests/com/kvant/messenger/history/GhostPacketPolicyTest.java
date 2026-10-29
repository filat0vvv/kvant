// SPDX-License-Identifier: GPL-2.0-or-later
package com.kvant.messenger.history;
import org.junit.Test;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import static org.junit.Assert.*;

public class GhostPacketPolicyTest {
    @Test public void ghostBlocksEveryReadRequestType() {
        TLObject[] requests={new TLRPC.TL_messages_readHistory(),new TLRPC.TL_messages_readEncryptedHistory(),
                new TLRPC.TL_messages_readDiscussion(),new TLRPC.TL_messages_readMessageContents(),
                new TLRPC.TL_channels_readHistory(),new TLRPC.TL_channels_readMessageContents()};
        for(TLObject request:requests) {
            assertTrue(GhostPacketPolicy.blocksRead(request,false));
            assertFalse(GhostPacketPolicy.blocksRead(request,true));
        }
        assertFalse(GhostPacketPolicy.blocksRead(new TLRPC.TL_messages_sendMessage(),false));
    }
    @Test public void typingAndOnlineAreControlledWithoutBlockingMessages() {
        assertTrue(GhostPacketPolicy.blocksTyping(new TLRPC.TL_messages_setTyping(),false));
        assertTrue(GhostPacketPolicy.blocksTyping(new TLRPC.TL_messages_setEncryptedTyping(),false));
        assertFalse(GhostPacketPolicy.blocksTyping(new TLRPC.TL_messages_sendMessage(),false));
        TLRPC.TL_account_updateStatus status=new TLRPC.TL_account_updateStatus();
        GhostPacketPolicy.applyOnlinePolicy(status,false);assertTrue(status.offline);
        TLRPC.TL_account_updateStatus normal=new TLRPC.TL_account_updateStatus();
        GhostPacketPolicy.applyOnlinePolicy(normal,true);assertFalse(normal.offline);
    }
}
