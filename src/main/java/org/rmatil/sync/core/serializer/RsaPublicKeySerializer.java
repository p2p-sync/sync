package org.rmatil.sync.core.serializer;

import com.google.gson.*;
import org.rmatil.sync.core.security.KeyPairUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;

public class RsaPublicKeySerializer implements JsonSerializer<RSAPublicKey>, JsonDeserializer<RSAPublicKey> {

    @Override
    public JsonElement serialize(RSAPublicKey rsaPublicKey, Type type, JsonSerializationContext jsonSerializationContext) {
        String stringRepresentation = KeyPairUtils.publicKeyToString(rsaPublicKey);

        return new JsonPrimitive(stringRepresentation);
    }

    @Override
    public RSAPublicKey deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
            throws JsonParseException {
        try {
            return KeyPairUtils.publicKeyFromString(jsonElement.getAsJsonPrimitive().getAsString());
        } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException e) {
            throw new JsonParseException(e);
        }
    }
}
