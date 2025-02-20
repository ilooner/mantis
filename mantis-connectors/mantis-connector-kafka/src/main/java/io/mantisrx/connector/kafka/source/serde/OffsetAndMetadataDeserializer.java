/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.mantisrx.connector.kafka.source.serde;

import io.mantisrx.shaded.com.fasterxml.jackson.core.JsonParser;
import io.mantisrx.shaded.com.fasterxml.jackson.databind.DeserializationContext;
import io.mantisrx.shaded.com.fasterxml.jackson.databind.JsonDeserializer;
import io.mantisrx.shaded.com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;

public class OffsetAndMetadataDeserializer extends JsonDeserializer<OffsetAndMetadata> {
    @Override
    public OffsetAndMetadata deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        final JsonNode node = p.getCodec().readTree(p);

        final long offset = node.get("offset").longValue();

        final String metadata = node.get("metadata").textValue();

        return new OffsetAndMetadata(offset, metadata);
    }
}
