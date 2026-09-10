package com.hospital.platform.payment;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** Do not silently truncate a JSON decimal such as 1000.9 to 1000 cents. */
public class MinorAmountDeserializer extends JsonDeserializer<Long> {
  @Override public Long deserialize(JsonParser parser,DeserializationContext context)throws IOException {
    if(parser.currentToken()!=JsonToken.VALUE_NUMBER_INT) throw new PaymentException("INVALID_MONEY",400);
    return parser.getLongValue();
  }
}
