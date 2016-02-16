package org.rmatil.sync.test.messaging.fileexchange.offer;

import org.junit.Test;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandlerResult;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferResponse;
import org.rmatil.sync.network.core.model.NodeLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class FileOfferExchangeHandlerResultTest {

    @Test
    public void test() {
        NodeLocation nodeLocation = new NodeLocation(UUID.randomUUID(), null);
        FileOfferResponse response = new FileOfferResponse(UUID.randomUUID(), StatusCode.ACCEPTED, null, nodeLocation);
        List<FileOfferResponse> fileOfferResponses = new ArrayList<>();
        fileOfferResponses.add(response);

        FileOfferExchangeHandlerResult fileOfferExchangeHandlerResult = new FileOfferExchangeHandlerResult(fileOfferResponses);

        assertEquals("FileOfferResponses are not equal", fileOfferResponses, fileOfferExchangeHandlerResult.getFileOfferResponses());
    }
}
