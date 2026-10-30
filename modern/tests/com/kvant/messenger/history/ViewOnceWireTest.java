package com.kvant.messenger.history;

import org.junit.Test;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import static org.junit.Assert.*;

public class ViewOnceWireTest {
    private TLRPC.TL_inputFile file() {
        TLRPC.TL_inputFile file=new TLRPC.TL_inputFile();file.id=1;file.parts=1;
        file.name="media";file.md5_checksum="";return file;
    }
    private TLRPC.InputMedia roundTrip(TLRPC.InputMedia media) {
        SerializedData data=new SerializedData();media.serializeToStream(data);
        SerializedData input=new SerializedData(data.toByteArray());
        TLRPC.InputMedia result=TLRPC.InputMedia.TLdeserialize(input,input.readInt32(true),true);
        input.cleanup();data.cleanup();return result;
    }
    @Test public void photosKeepTheServerViewOnceMarker() {
        TLRPC.TL_inputMediaUploadedPhoto photo=new TLRPC.TL_inputMediaUploadedPhoto();
        photo.file=file();photo.flags=2;photo.ttl_seconds=Integer.MAX_VALUE;
        assertEquals(Integer.MAX_VALUE,roundTrip(photo).ttl_seconds);
    }
    @Test public void voiceAndRoundVideosKeepViewOnceAndTheirMediaKind() {
        for(int kind=0;kind<3;kind++) {
            boolean voice=kind==0;
            TLRPC.TL_inputMediaUploadedDocument media=new TLRPC.TL_inputMediaUploadedDocument();
            media.file=file();media.flags=2;media.ttl_seconds=Integer.MAX_VALUE;media.mime_type=voice?"audio/ogg":"video/mp4";
            if(voice) { TLRPC.TL_documentAttributeAudio a=new TLRPC.TL_documentAttributeAudio();a.voice=true;a.duration=1;media.attributes.add(a); }
            else { TLRPC.TL_documentAttributeVideo a=new TLRPC.TL_documentAttributeVideo();a.round_message=kind==2;a.duration=1;a.w=240;a.h=240;media.attributes.add(a); }
            TLRPC.InputMedia restored=roundTrip(media);
            assertEquals(Integer.MAX_VALUE,restored.ttl_seconds);
            assertEquals(voice,restored.attributes.get(0) instanceof TLRPC.TL_documentAttributeAudio);
            if(voice)assertTrue(restored.attributes.get(0).voice);else assertEquals(kind==2,restored.attributes.get(0).round_message);
        }
    }
}
