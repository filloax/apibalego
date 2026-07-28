package com.ruslan.apibalego;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ruslan.apibalego.api.Apibalego;
import com.ruslan.apibalego.http.ApiEntry;
import com.ruslan.apibalego.http.ApiEntryHandler;
import com.ruslan.apibalego.http.ApiEntryRaw;
import com.ruslan.apibalego.http.ApiEntryRegistry;
import com.ruslan.apibalego.http.ApiEntryType;
import com.ruslan.apibalego.socket.ResponseSender;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

public class ApibalegoJavaUsageTest {
    public record TestDetails(String value, int count) {
        public static final Codec<TestDetails> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.STRING.fieldOf("value").forGetter(TestDetails::value),
                Codec.INT.fieldOf("count").forGetter(TestDetails::count)
        ).apply(builder, TestDetails::new));
    }

    private static JsonElement json(String raw) {
        return Json.Default.parseToJsonElement(raw);
    }

    @Test
    void codecDetailsAreParsedAndDeliveredToUpdateHandler() {
        var key = Identifier.fromNamespaceAndPath("unittest", "java_codec_" + System.nanoTime());
        var server = mock(MinecraftServer.class);
        var received = new AtomicReference<TestDetails>();

        Apibalego.registerApiHandler(key, TestDetails.CODEC, TestDetails.class, new ApiEntryHandler<TestDetails>() {
            @Override
            public void handleApiUpdate(MinecraftServer server, java.util.Collection<ApiEntry<TestDetails>> entries) {
                entries.stream().findFirst().ifPresent(entry -> received.set(entry.getDetails()));
            }
        });

        ApiEntryType<?> type = ApiEntryRegistry.INSTANCE.lookupRaw(key);
        type.dispatchUpdate(
                List.of(new ApiEntryRaw(type, json("{\"value\": \"hello\", \"count\": 42}"), "test-id", true)),
                server
        );

        assertEquals(new TestDetails("hello", 42), received.get());
    }

    @Test
    void classDetailsAreBoundWithGson() {
        var key = Identifier.fromNamespaceAndPath("unittest", "java_class_" + System.nanoTime());
        var server = mock(MinecraftServer.class);
        var received = new AtomicReference<TestDetails>();

        Apibalego.registerApiHandler(key, TestDetails.class, (handlerServer, entries) ->
                entries.stream().findFirst().ifPresent(entry -> received.set(entry.getDetails())));

        ApiEntryType<?> type = ApiEntryRegistry.INSTANCE.lookupRaw(key);
        type.dispatchUpdate(
                List.of(new ApiEntryRaw(type, json("{\"value\": \"hello\", \"count\": 42}"), "test-id", true)),
                server
        );

        assertEquals(new TestDetails("hello", 42), received.get());
    }

    @Test
    void rawJsonDetailsAreHandedOverUnparsed() {
        var key = Identifier.fromNamespaceAndPath("unittest", "java_json_" + System.nanoTime());
        var server = mock(MinecraftServer.class);
        var received = new AtomicReference<com.google.gson.JsonElement>();

        Apibalego.registerJsonApiHandler(key, (handlerServer, entries) ->
                entries.stream().findFirst().ifPresent(entry -> received.set(entry.getDetails())));

        ApiEntryType<?> type = ApiEntryRegistry.INSTANCE.lookupRaw(key);
        type.dispatchUpdate(
                List.of(new ApiEntryRaw(type, json("{\"anything\": [1, 2]}"), "test-id", true)),
                server
        );

        assertEquals(2, received.get().getAsJsonObject().getAsJsonArray("anything").size());
    }

    @Test
    void simpleEntriesHaveNoDetails() {
        var key = Identifier.fromNamespaceAndPath("unittest", "java_simple_" + System.nanoTime());
        var server = mock(MinecraftServer.class);
        var receivedId = new AtomicReference<String>();
        var receivedDetails = new AtomicReference<Object>();

        Apibalego.registerSimple(key, (handlerServer, entries) -> entries.stream().findFirst().ifPresent(entry -> {
            receivedId.set(entry.getId());
            receivedDetails.set(entry.getDetails());
        }));

        ApiEntryType<?> type = ApiEntryRegistry.INSTANCE.lookupRaw(key);
        type.dispatchUpdate(
                List.of(new ApiEntryRaw(type, null, "simple-id", true)),
                server
        );

        assertEquals("simple-id", receivedId.get());
        assertNull(receivedDetails.get());
    }

    @Test
    void liveUpdateEventPayloadIsParsedWithCodec() {
        var eventName = "java_live_codec_" + System.nanoTime();
        var server = mock(MinecraftServer.class);
        var received = new AtomicReference<TestDetails>();

        var event = Apibalego.registerLiveUpdateEvent(eventName, TestDetails.CODEC, TestDetails.class,
                (data, handlerServer, sender) -> {
                    received.set(data);
                    sender.sendSuccess(Map.of());
                });

        event.dispatch("{\"value\": \"live\", \"count\": 7}", server, mock(ResponseSender.class));

        assertEquals(new TestDetails("live", 7), received.get());
    }

    @Test
    void liveUpdateEventPayloadIsBoundWithGson() {
        var eventName = "java_live_class_" + System.nanoTime();
        var server = mock(MinecraftServer.class);
        var received = new AtomicReference<TestDetails>();

        var event = Apibalego.registerLiveUpdateEvent(eventName, TestDetails.class,
                (data, handlerServer, sender) -> received.set(data));

        event.dispatch("{\"value\": \"live\", \"count\": 7}", server, mock(ResponseSender.class));

        assertEquals(new TestDetails("live", 7), received.get());
    }

    @Test
    void simpleLiveUpdateEventHasNoPayload() {
        var eventName = "java_live_simple_" + System.nanoTime();
        var server = mock(MinecraftServer.class);
        var called = new AtomicReference<Boolean>(false);

        var event = Apibalego.registerSimpleLiveUpdateEvent(eventName,
                (handlerServer, sender) -> called.set(true));

        event.dispatch("", server, mock(ResponseSender.class));

        assertEquals(true, called.get());
    }
}
