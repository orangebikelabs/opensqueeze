/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;
import com.google.common.io.CountingInputStream;
import com.google.common.io.CountingOutputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class JsonHelper {
    final static private SmileFactory sSmileFactory;
    final static private JsonFactory sJsonFactory;
    final static private ObjectMapper sObjectMapper;
    final static private ObjectMapper sSmileObjectMapper;

    static {
        sSmileFactory = new SmileFactory();
        sJsonFactory = new JsonFactory();

        sObjectMapper = new ObjectMapper(sJsonFactory);
        sObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        sSmileObjectMapper = new ObjectMapper(sSmileFactory);
        sSmileObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    @Nonnull
    public static JsonParser createParserForData(ByteSource byteSource) throws IOException {
        DataFormatDetector detector = new DataFormatDetector(Arrays.asList(sSmileFactory, sJsonFactory));
        InputStream unbufferedStream = byteSource.openStream();
        if (unbufferedStream == null) {
            throw new IOException("null input stream for json parser");
        }
        DataFormatMatcher matcher = detector.findFormat(new BufferedInputStream(unbufferedStream));
        JsonParser parser = matcher.createParserWithMatch();
        if (parser == null) {
            throw new IOException("no parser found for data");
        }
        parser.enable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
        return parser;
    }

    @Nonnull
    public static JsonParser createParserForData(InputStream is) throws IOException {
        DataFormatDetector detector = new DataFormatDetector(Arrays.asList(sSmileFactory, sJsonFactory));
        DataFormatMatcher matcher = detector.findFormat(is);
        return matcher.createParserWithMatch();
    }

    @Nonnull
    public static JsonFactory getJsonFactory() {
        return sJsonFactory;
    }

    @Nonnull
    public static ObjectMapper getJsonObjectMapper() {
        return sObjectMapper;
    }

    @Nonnull
    public static ObjectWriter getJsonObjectWriter() {
        return sObjectMapper.writer();
    }

    @Nonnull
    public static ObjectReader getJsonObjectReader() {
        return sObjectMapper.reader();
    }

    @Nonnull
    public static ObjectWriter getSmileObjectWriter() {
        return sSmileObjectMapper.writer();
    }

    public static void compactSerializeNode(JsonNode value, OutputStream outputStream, AtomicInteger outSize) throws IOException {
        CountingOutputStream cos = new CountingOutputStream(outputStream);
        try {
            sSmileObjectMapper.writeValue(cos, value);
        } finally {
            cos.flush();
        }
        outSize.set((int) cos.getCount());
    }

    public static JsonNode deserializeNode(ByteSource byteSource, AtomicInteger outSize) throws IOException {
        CountingInputStream cis = new CountingInputStream(byteSource.openStream());
        try {
            JsonParser parser = createParserForData(cis);
            JsonNode retval = parser.readValueAsTree();
            outSize.set((int) cis.getCount());
            return retval;
        } finally {
            cis.close();
        }
    }

    @Nonnull
    public static String toString(JsonNode node) {
        try {
            return getJsonObjectWriter().withDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            OSLog.i("json pretty print problem: " + node, e);
            return node.toString();
        }
    }

    @Nullable
    public static String getString(JsonNode node, String key, @Nullable String defaultValue) {
        String retval;
        JsonNode value = node.get(key);
        if (value != null && !value.isNull()) {
            retval = value.asText();
        } else {
            retval = defaultValue;
        }
        return retval;
    }

    public static LinkedHashMap<String, String> getMap(JsonNode parent, String key) {
        JsonNode node = parent.get(key);
        if(node == null || !node.isObject()) {
            return new LinkedHashMap<>();
        }

        LinkedHashMap<String, String> retval = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fit = node.fields();
        while (fit.hasNext()) {
            Map.Entry<String, JsonNode> entry = fit.next();
            JsonNode value = entry.getValue();
            if(!value.isNull()) {
                retval.put(entry.getKey(), value.asText());
            }
        }
        return retval;
    }

    @Nonnull
    public static Iterable<ObjectNode> getObjects(JsonNode node) {
        if (node.isArray()) {
            return Iterables.filter(node, ObjectNode.class);
        } else if (node.isObject()) {
            return Collections.singletonList((ObjectNode) node);
        } else {
            return Collections.emptyList();
        }
    }

    public static boolean isResponseCacheable(JsonNode node) {
        return node.path("rescan").asInt() == 0;
    }
}
