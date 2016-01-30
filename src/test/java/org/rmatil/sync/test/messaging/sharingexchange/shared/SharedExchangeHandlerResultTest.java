package org.rmatil.sync.test.messaging.sharingexchange.shared;

import org.junit.Test;
import org.rmatil.sync.core.messaging.sharingexchange.shared.SharedExchangeHandlerResult;

import static org.junit.Assert.*;

public class SharedExchangeHandlerResultTest {

    @Test
    public void test() {
        SharedExchangeHandlerResult result = new SharedExchangeHandlerResult(true);

        assertTrue("Should be true", result.hasAccepted());
    }
}
