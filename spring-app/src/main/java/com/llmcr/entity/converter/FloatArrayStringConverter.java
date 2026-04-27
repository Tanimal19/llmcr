package com.llmcr.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class FloatArrayStringConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null || attribute.length == 0) {
            return null;
        }

        StringBuilder serialized = new StringBuilder();
        for (int i = 0; i < attribute.length; i++) {
            if (i > 0) {
                serialized.append(',');
            }
            serialized.append(attribute[i]);
        }
        return serialized.toString();
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }

        String[] rawValues = dbData.split(",");
        float[] values = new float[rawValues.length];
        for (int i = 0; i < rawValues.length; i++) {
            values[i] = Float.parseFloat(rawValues[i]);
        }
        return values;
    }
}
