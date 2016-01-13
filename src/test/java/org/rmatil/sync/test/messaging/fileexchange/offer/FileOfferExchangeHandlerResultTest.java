package org.rmatil.sync.test.messaging.fileexchange.offer;

import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandlerResult;

import static org.junit.Assert.*;

public class FileOfferExchangeHandlerResultTest {

    @Test
    public void test() {
        FileOfferExchangeHandlerResult fileOfferExchangeHandlerResult = new FileOfferExchangeHandlerResult(true, true);

        assertTrue("Offer should be accepted", fileOfferExchangeHandlerResult.hasOfferAccepted());
        assertTrue("Offer should have been a conflict", fileOfferExchangeHandlerResult.hasConflictDetected());
    }
}
