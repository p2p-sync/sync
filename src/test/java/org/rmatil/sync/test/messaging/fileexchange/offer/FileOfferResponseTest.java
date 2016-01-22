package org.rmatil.sync.test.messaging.fileexchange.offer;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class FileOfferResponseTest {

    protected static final UUID           EXCHANGE_ID       = UUID.randomUUID();
    protected static final UUID           CLIENT_DEVICE_ID  = UUID.randomUUID();
    protected static final ClientDevice   CLIENT_DEVICE     = new ClientDevice("Norman Gordon", CLIENT_DEVICE_ID, null);
    protected static final ClientLocation CLIENT_LOCATION   = new ClientLocation(CLIENT_DEVICE_ID, null);
    protected static final boolean        ACCEPTED_OFFER    = true;
    protected static final boolean        CONFLICT_DETECTED = true;
    protected static final boolean REQUEST_OBSOLETE = true;

    protected static FileOfferResponse fileOfferResponse;

    @BeforeClass
    public static void setUp() {
        fileOfferResponse = new FileOfferResponse(
                EXCHANGE_ID,
                CLIENT_DEVICE,
                CLIENT_LOCATION,
                ACCEPTED_OFFER,
                CONFLICT_DETECTED,
                REQUEST_OBSOLETE
        );
    }

    @Test
    public void test() {
        assertEquals("ExchangeId not equal", EXCHANGE_ID, fileOfferResponse.getExchangeId());
        assertEquals("ClientDevice not equal", CLIENT_DEVICE, fileOfferResponse.getClientDevice());
        assertEquals("ClientLocation not equal", CLIENT_LOCATION, fileOfferResponse.getReceiverAddress());
        assertEquals("AcceptedOffer not equal", ACCEPTED_OFFER, fileOfferResponse.hasAcceptedOffer());
        assertEquals("ConflictDetected not equal", CONFLICT_DETECTED, fileOfferResponse.hasConflict());
        assertEquals("RequestObsolete not equal", REQUEST_OBSOLETE, fileOfferResponse.isRequestObsolete());
    }
}
