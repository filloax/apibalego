package com.ruslan.apibalego.docs

import com.ruslan.apibalego.client.http.ClientApiEntryRegistry
import com.ruslan.apibalego.http.ApiDetailsParser
import com.ruslan.apibalego.http.ApiEntryRegistry
import com.ruslan.apibalego.socket.LiveUpdatesEventRegistry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import java.nio.file.Files
import java.nio.file.Path

/**
 * Dumps the currently registered [ApiEntryRegistry]/[ClientApiEntryRegistry]/[LiveUpdatesEventRegistry]
 * entries to markdown, reading DTO structure off each entry's [KSerializer.descriptor]. Payload
 * descriptions are not sourced from KDoc (not retained at runtime) - files are meant to be hand
 * annotated afterwards, and are overwritten on every generation.
 */
object ApiDocGenerator {
    fun generate(outDir: Path) {
        Files.createDirectories(outDir)
        Files.writeString(outDir.resolve("server-api.md"), buildServerDoc())
        Files.writeString(outDir.resolve("client-api.md"), buildClientDoc())
        Files.writeString(outDir.resolve("live-sync-events.md"), buildLiveSyncDoc())
    }

    private fun buildServerDoc(): String = buildString {
        append("# Apibalego server API entries\n\n")
        append(HEADER_NOTE)
        ApiEntryRegistry.all().entries.sortedBy { it.key.toString() }.forEach { (key, type) ->
            append("## `$key`\n\n")
            appendPayload(this, type.detailsParser, type.detailsType.simpleName)
        }
    }

    private fun buildClientDoc(): String = buildString {
        append("# Apibalego client API entries\n\n")
        append(HEADER_NOTE)
        ClientApiEntryRegistry.all().entries.sortedBy { it.key.toString() }.forEach { (key, type) ->
            append("## `$key`\n\n")
            appendPayload(this, type.detailsParser, type.detailsType.simpleName)
        }
    }

    private fun buildLiveSyncDoc(): String = buildString {
        append("# Apibalego live sync (websocket) events\n\n")
        append(HEADER_NOTE)
        LiveUpdatesEventRegistry.all().entries.sortedBy { it.key }.forEach { (eventName, event) ->
            append("## `$eventName`\n\n")
            val deserializer = event.deserializer
            when {
                deserializer != null -> appendSerializerPayload(this, deserializer, deserializer.descriptor.serialName)
                event.payloadType != null -> appendOpaquePayload(this, event.payloadType?.simpleName)
                else -> append("No payload.\n\n")
            }
        }
    }

    private fun appendPayload(sb: StringBuilder, parser: ApiDetailsParser<*>?, typeName: String?) {
        when (parser) {
            null -> sb.append("No payload.\n\n")
            is ApiDetailsParser.Kotlinx -> appendSerializerPayload(sb, parser.serializer, typeName)
            else -> appendOpaquePayload(sb, typeName)
        }
    }

    // codec/class/raw json types have no runtime field listing, only the name is known
    private fun appendOpaquePayload(sb: StringBuilder, typeName: String?) {
        sb.append("Payload: `$typeName` (structure not introspectable, defined by the owning mod)\n\n")
    }

    private fun appendSerializerPayload(sb: StringBuilder, deserializer: KSerializer<*>?, typeName: String?) {
        if (deserializer == null) {
            sb.append("No payload.\n\n")
            return
        }
        sb.append("Payload: `$typeName`\n\n")
        appendDescriptor(sb, deserializer.descriptor, "", mutableSetOf())
        sb.append("\n")
    }

    private fun appendDescriptor(sb: StringBuilder, descriptor: SerialDescriptor, indent: String, seen: MutableSet<String>) {
        if (descriptor.elementsCount == 0 || !seen.add(descriptor.serialName)) return
        for (i in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(i)
            val elem = descriptor.getElementDescriptor(i)
            sb.append("$indent- `$name`: ${typeLabel(elem)}\n")
            val nested = if (elem.kind == StructureKind.LIST && elem.elementsCount > 0) elem.getElementDescriptor(0) else elem
            if (nested.kind == StructureKind.CLASS) {
                appendDescriptor(sb, nested, "$indent  ", seen)
            }
        }
        seen.remove(descriptor.serialName)
    }

    private fun typeLabel(d: SerialDescriptor): String = when (d.kind) {
        is PrimitiveKind -> simpleName(d)
        StructureKind.LIST -> "List<${if (d.elementsCount > 0) typeLabel(d.getElementDescriptor(0)) else "?"}>"
        StructureKind.MAP -> {
            val key = if (d.elementsCount > 0) typeLabel(d.getElementDescriptor(0)) else "?"
            val value = if (d.elementsCount > 1) typeLabel(d.getElementDescriptor(1)) else "?"
            "Map<$key, $value>"
        }
        SerialKind.ENUM -> "enum ${simpleName(d)}"
        else -> simpleName(d)
    }

    private fun simpleName(d: SerialDescriptor) = d.serialName.substringAfterLast('.')

    private const val HEADER_NOTE = "Auto-generated from currently registered entry types, should also include other mods using Apibalego.\n\n"
}
