package org.rmatil.sync.core.serializer;

import com.google.gson.*;
import org.rmatil.sync.core.security.KeyPairUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;

public class RsaPrivateKeySerializer implements JsonSerializer<RSAPrivateKey>, JsonDeserializer<RSAPrivateKey> {

    @Override
    public JsonElement serialize(RSAPrivateKey rsaPrivateKey, Type typeOfSrc, JsonSerializationContext context) {
        String stringRepresentation = KeyPairUtils.privateKeyToString(rsaPrivateKey);

        return new JsonPrimitive(stringRepresentation);
    }

    @Override
    public RSAPrivateKey deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        try {
            return KeyPairUtils.privateKeyFromString(json.getAsJsonPrimitive().getAsString());
        } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException e) {
            throw new JsonParseException(e);
        }
    }
}
