package org.rmatil.sync.test.base;

import org.junit.Test;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.AMessage;

import java.util.UUID;

import static org.junit.Assert.*;

public class AMessageTest {

    protected static final UUID EXCHANGE_ID = UUID.randomUUID();
    protected static final UUID EXCHANGE_ID_2 = UUID.randomUUID();
    protected static final StatusCode STATUS_CODE = StatusCode.ACCEPTED;
    protected static final StatusCode STATUS_CODE_2 = StatusCode.DENIED;

    protected class DummyMessage extends AMessage {

        /**
         * @param exchangeId The exchangeId of the message
         * @param statusCode The status code of the message
         */
        protected DummyMessage(UUID exchangeId, StatusCode statusCode) {
            super(exchangeId, statusCode);
        }
    }

    @Test
    public void test() {
        DummyMessage dummyMessage = new DummyMessage(
                EXCHANGE_ID,
                STATUS_CODE
        );

        assertEquals("ExchangeId should be equal", EXCHANGE_ID, dummyMessage.getExchangeId());
        assertEquals("StatusCode should be equal", STATUS_CODE, dummyMessage.getStatusCode());

        dummyMessage.setExchangeId(EXCHANGE_ID_2);
        dummyMessage.setStatusCode(STATUS_CODE_2);

        assertEquals("ExchangeId should be equal after changing", EXCHANGE_ID_2, dummyMessage.getExchangeId());
        assertEquals("StatusCode should be equal after changing", STATUS_CODE_2, dummyMessage.getStatusCode());



    }
}
