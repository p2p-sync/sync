package org.rmatil.sync.test.messaging.fileexchange.offer;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.core.messaging.fileexchange.offer.SerializableEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.test.base.BaseMessageTest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class FileOfferRequestTest extends BaseMessageTest {

    protected static final Path           PATH             = Paths.get("path/to/myFile.txt");
    protected static final Path           NEW_PATH         = Paths.get("newPath/to/my/myFile.txt");
    protected static final long           TIMESTAMP        = 1234567890;
    protected static final Path           FILE_NAME        = PATH.getFileName();
    protected static final String         HASH             = "thisIsAHash";
    protected static final String         HASH_BEFORE      = null;
    protected static final boolean        IS_FILE          = true;
    protected static final UUID           CLIENT_DEVICE_ID = UUID.randomUUID();
    protected static final ClientDevice   CLIENT_DEVICE    = new ClientDevice("Norman Gordon", CLIENT_DEVICE_ID, null);
    protected static final ClientLocation CLIENT_LOCATION  = new ClientLocation(CLIENT_DEVICE_ID, null);

    protected static MoveEvent         moveEvent;
    protected static SerializableEvent serializableEvent;
    protected static FileOfferRequest  fileOfferRequest;

    @BeforeClass
    public static void setUp() {
        List<ClientLocation> clientLocations = new ArrayList<>();
        clientLocations.add(CLIENT_LOCATION);

        moveEvent = new MoveEvent(
                PATH,
                NEW_PATH,
                FILE_NAME.toString(),
                HASH,
                TIMESTAMP
        );

        serializableEvent = SerializableEvent.fromEvent(moveEvent, HASH_BEFORE, IS_FILE);

        fileOfferRequest = new FileOfferRequest(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                serializableEvent,
                clientLocations
        );
    }

    @Test
    public void test() {
        assertEquals("ExchangeId not equal", EXCHANGE_ID, fileOfferRequest.getExchangeId());
        assertEquals("StatusCode is not equal", STATUS_CODE, fileOfferRequest.getStatusCode());
        assertEquals("ClientDevice not equal", CLIENT_DEVICE, fileOfferRequest.getClientDevice());
        assertEquals("Event is not equal", serializableEvent, fileOfferRequest.getEvent());
        assertThat("Receiver addresses should contain clientLocation", fileOfferRequest.getReceiverAddresses(), hasItem(CLIENT_LOCATION));
    }
}
