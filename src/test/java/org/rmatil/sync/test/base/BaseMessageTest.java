package org.rmatil.sync.test.base;

import org.rmatil.sync.core.messaging.StatusCode;

import java.util.UUID;

public class BaseMessageTest {

    protected static final UUID       EXCHANGE_ID = UUID.randomUUID();
    protected static final StatusCode STATUS_CODE = StatusCode.ACCEPTED;
}
