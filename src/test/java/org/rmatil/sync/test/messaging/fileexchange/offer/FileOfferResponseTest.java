package org.rmatil.sync.test.messaging.fileexchange.offer;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.test.base.BaseMessageTest;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class FileOfferResponseTest extends BaseMessageTest {

    protected static final UUID           CLIENT_DEVICE_ID = UUID.randomUUID();
    protected static final ClientDevice   CLIENT_DEVICE    = new ClientDevice("Norman Gordon", CLIENT_DEVICE_ID, null);
    protected static final NodeLocation CLIENT_LOCATION  = new NodeLocation(CLIENT_DEVICE_ID, null);

    protected static FileOfferResponse fileOfferResponse;

    @BeforeClass
    public static void setUp() {
        fileOfferResponse = new FileOfferResponse(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                CLIENT_LOCATION
        );
    }

    @Test
    public void test() {
        assertEquals("ExchangeId not equal", EXCHANGE_ID, fileOfferResponse.getExchangeId());
        assertEquals("StatusCode not equal", STATUS_CODE, fileOfferResponse.getStatusCode());
        assertEquals("ClientDevice not equal", CLIENT_DEVICE, fileOfferResponse.getClientDevice());
        assertEquals("NodeLocation not equal", CLIENT_LOCATION, fileOfferResponse.getReceiverAddress());
    }
}
