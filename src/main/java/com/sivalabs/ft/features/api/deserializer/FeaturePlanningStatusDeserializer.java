package com.sivalabs.ft.features.api.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import java.io.IOException;

public class FeaturePlanningStatusDeserializer extends JsonDeserializer<FeaturePlanningStatus> {

    @Override
    public FeaturePlanningStatus deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getText();
        try {
            return FeaturePlanningStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new JsonMappingException(parser, "Invalid featurePlanningStatus value: '%s'.".formatted(value));
        }
    }
}
