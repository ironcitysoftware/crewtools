/**
 * Copyright 2018 Iron City Software LLC
 *
 * This file is part of Checkind.
 *
 * Checkind is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Checkind is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Checkind.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.google.protobuf.util;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import com.google.common.base.Splitter;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;

import crewtools.aa.Proto;

/**
 * The original source of this class is from the Google protobuffer repository.
 * It has been altered to suit our needs.
 */

/**
 * Utility classes to convert protobuf messages to/from JSON format. The JSON
 * format follows Proto3 JSON specification and only proto3 features are
 * supported. Proto2 only features (e.g., extensions and unknown fields) will
 * be discarded in the conversion. That is, when converting proto2 messages
 * to JSON format, extensions and unknown fields will be treated as if they
 * do not exist. This applies to proto2 messages embedded in proto3 messages
 * as well.
 */
public class JsonFormat {
  private static final Logger logger = Logger.getLogger(JsonFormat.class.getName());

  private JsonFormat() {}

  static class CamelCaser {
    private static final Splitter SPLITTER = Splitter.on('_');

    public String getCamelCase(String name) {
      if (name.indexOf('_') == -1) {
        return name;
      }
      StringBuilder camelName = new StringBuilder();
      List<String> segments = SPLITTER.splitToList(name);
      camelName.append(segments.get(0));
      for (int i = 1; i < segments.size(); ++i) {
        camelName.append(segments.get(i).substring(0, 1).toUpperCase());
        camelName.append(segments.get(i).substring(1));
      }
      return camelName.toString();
    }
  }



  /**
   * Creates a {@link Printer} with default configurations.
   */
  public static Printer printer() {
    return new Printer(TypeRegistry.getEmptyTypeRegistry(), false, false, false);
  }

  /**
   * A Printer converts protobuf message to JSON format.
   */
  public static class Printer {
    private final TypeRegistry registry;
    private final boolean includingDefaultValueFields;
    private final boolean preservingProtoFieldNames;
    private final boolean omittingInsignificantWhitespace;

    private Printer(
        TypeRegistry registry,
        boolean includingDefaultValueFields,
        boolean preservingProtoFieldNames,
        boolean omittingInsignificantWhitespace) {
      this.registry = registry;
      this.includingDefaultValueFields = includingDefaultValueFields;
      this.preservingProtoFieldNames = preservingProtoFieldNames;
      this.omittingInsignificantWhitespace = omittingInsignificantWhitespace;
    }

    /**
     * Creates a new {@link Printer} using the given registry. The new Printer
     * clones all other configurations from the current {@link Printer}.
     *
     * @throws IllegalArgumentException if a registry is already set.
     */
    public Printer usingTypeRegistry(TypeRegistry registry) {
      if (this.registry != TypeRegistry.getEmptyTypeRegistry()) {
        throw new IllegalArgumentException("Only one registry is allowed.");
      }
      return new Printer(
          registry,
          includingDefaultValueFields,
          preservingProtoFieldNames,
          omittingInsignificantWhitespace);
    }

    /**
     * Creates a new {@link Printer} that will also print fields set to their
     * defaults. Empty repeated fields and map fields will be printed as well.
     * The new Printer clones all other configurations from the current
     * {@link Printer}.
     */
    public Printer includingDefaultValueFields() {
      return new Printer(
          registry, true, preservingProtoFieldNames, omittingInsignificantWhitespace);
    }

    /**
     * Creates a new {@link Printer} that is configured to use the original proto
     * field names as defined in the .proto file rather than converting them to
     * lowerCamelCase. The new Printer clones all other configurations from the
     * current {@link Printer}.
     */
    public Printer preservingProtoFieldNames() {
      return new Printer(
          registry, includingDefaultValueFields, true, omittingInsignificantWhitespace);
    }


    /**
     * Create a new  {@link Printer}  that will omit all insignificant whitespace
     * in the JSON output. This new Printer clones all other configurations from the
     * current Printer. Insignificant whitespace is defined by the JSON spec as whitespace
     * that appear between JSON structural elements:
     * <pre>
     * ws = *(
     * %x20 /              ; Space
     * %x09 /              ; Horizontal tab
     * %x0A /              ; Line feed or New line
     * %x0D )              ; Carriage return
     * </pre>
     * See <a href="https://tools.ietf.org/html/rfc7159">https://tools.ietf.org/html/rfc7159</a>
     * current {@link Printer}.
     */
    public Printer omittingInsignificantWhitespace() {
      return new Printer(registry, includingDefaultValueFields, preservingProtoFieldNames, true);
    }

    /**
     * Converts a protobuf message to JSON format.
     *
     * @throws InvalidProtocolBufferException if the message contains Any types
     *         that can't be resolved.
     * @throws IOException if writing to the output fails.
     */
    public void appendTo(MessageOrBuilder message, Appendable output) throws IOException {
      // TODO(xiaofeng): Investigate the allocation overhead and optimize for
      // mobile.
      new PrinterImpl(
              registry,
              includingDefaultValueFields,
              preservingProtoFieldNames,
              output,
              omittingInsignificantWhitespace)
          .print(message);
    }

    /**
     * Converts a protobuf message to JSON format. Throws exceptions if there
     * are unknown Any types in the message.
     */
    public String print(MessageOrBuilder message) throws InvalidProtocolBufferException {
      try {
        StringBuilder builder = new StringBuilder();
        appendTo(message, builder);
        return builder.toString();
      } catch (InvalidProtocolBufferException e) {
        throw e;
      } catch (IOException e) {
        // Unexpected IOException.
        throw new IllegalStateException(e);
      }
    }
  }

  /**
   * Creates a {@link Parser} with default configuration.
   */
  public static Parser parser() {
    return new Parser(TypeRegistry.getEmptyTypeRegistry(), false, Parser.DEFAULT_RECURSION_LIMIT);
  }

  /**
   * A Parser parses JSON to protobuf message.
   */
  public static class Parser {
    private final TypeRegistry registry;
    private final boolean ignoringUnknownFields;
    private final int recursionLimit;

    // The default parsing recursion limit is aligned with the proto binary parser.
    private static final int DEFAULT_RECURSION_LIMIT = 100;

    private Parser(TypeRegistry registry, boolean ignoreUnknownFields, int recursionLimit) {
      this.registry = registry;
      this.ignoringUnknownFields = ignoreUnknownFields;
      this.recursionLimit = recursionLimit;
    }

    /**
     * Creates a new {@link Parser} using the given registry. The new Parser
     * clones all other configurations from this Parser.
     *
     * @throws IllegalArgumentException if a registry is already set.
     */
    public Parser usingTypeRegistry(TypeRegistry registry) {
      if (this.registry != TypeRegistry.getEmptyTypeRegistry()) {
        throw new IllegalArgumentException("Only one registry is allowed.");
      }
      return new Parser(registry, ignoringUnknownFields, recursionLimit);
    }

    /**
     * Creates a new {@link Parser} configured to not throw an exception when an unknown field is
     * encountered. The new Parser clones all other configurations from this Parser.
     */
    public Parser ignoringUnknownFields() {
      return new Parser(this.registry, true, recursionLimit);
    }

    /**
     * Parses from JSON into a protobuf message.
     *
     * @throws InvalidProtocolBufferException if the input is not valid JSON
     *         format or there are unknown fields in the input.
     */
    public void merge(String json, Message.Builder builder) throws InvalidProtocolBufferException {
      // TODO(xiaofeng): Investigate the allocation overhead and optimize for
      // mobile.
      new ParserImpl(registry, ignoringUnknownFields, recursionLimit).merge(json, builder);
    }

    /**
     * Parses from JSON into a protobuf message.
     *
     * @throws InvalidProtocolBufferException if the input is not valid JSON
     *         format or there are unknown fields in the input.
     * @throws IOException if reading from the input throws.
     */
    public void merge(Reader json, Message.Builder builder) throws IOException {
      // TODO(xiaofeng): Investigate the allocation overhead and optimize for
      // mobile.
      new ParserImpl(registry, ignoringUnknownFields, recursionLimit).merge(json, builder);
    }

    // For testing only.
    Parser usingRecursionLimit(int recursionLimit) {
      return new Parser(registry, ignoringUnknownFields, recursionLimit);
    }
  }

  /**
   * A TypeRegistry is used to resolve Any messages in the JSON conversion.
   * You must provide a TypeRegistry containing all message types used in
   * Any message fields, or the JSON conversion will fail because data
   * in Any message fields is unrecognizable. You don't need to supply a
   * TypeRegistry if you don't use Any message fields.
   */
  public static class TypeRegistry {
    private static class EmptyTypeRegistryHolder {
      private static final TypeRegistry EMPTY =
          new TypeRegistry(Collections.<String, Descriptor>emptyMap());
    }

    public static TypeRegistry getEmptyTypeRegistry() {
      return EmptyTypeRegistryHolder.EMPTY;
    }

    public static Builder newBuilder() {
      return new Builder();
    }

    /**
     * Find a type by its full name. Returns null if it cannot be found in
     * this {@link TypeRegistry}.
     */
    public Descriptor find(String name) {
      return types.get(name);
    }

    private final Map<String, Descriptor> types;

    private TypeRegistry(Map<String, Descriptor> types) {
      this.types = types;
    }

    /**
     * A Builder is used to build {@link TypeRegistry}.
     */
    public static class Builder {
      private Builder() {}

      /**
       * Adds a message type and all types defined in the same .proto file as
       * well as all transitively imported .proto files to this {@link Builder}.
       */
      public Builder add(Descriptor messageType) {
        if (types == null) {
          throw new IllegalStateException("A TypeRegistry.Builer can only be used once.");
        }
        addFile(messageType.getFile());
        return this;
      }

      /**
       * Adds message types and all types defined in the same .proto file as
       * well as all transitively imported .proto files to this {@link Builder}.
       */
      public Builder add(Iterable<Descriptor> messageTypes) {
        if (types == null) {
          throw new IllegalStateException("A TypeRegistry.Builer can only be used once.");
        }
        for (Descriptor type : messageTypes) {
          addFile(type.getFile());
        }
        return this;
      }

      /**
       * Builds a {@link TypeRegistry}. This method can only be called once for
       * one Builder.
       */
      public TypeRegistry build() {
        TypeRegistry result = new TypeRegistry(types);
        // Make sure the built {@link TypeRegistry} is immutable.
        types = null;
        return result;
      }

      private void addFile(FileDescriptor file) {
        // Skip the file if it's already added.
        if (!files.add(file.getFullName())) {
          return;
        }
        for (FileDescriptor dependency : file.getDependencies()) {
          addFile(dependency);
        }
        for (Descriptor message : file.getMessageTypes()) {
          addMessage(message);
        }
      }

      private void addMessage(Descriptor message) {
        for (Descriptor nestedType : message.getNestedTypes()) {
          addMessage(nestedType);
        }

        if (types.containsKey(message.getFullName())) {
          logger.warning("Type " + message.getFullName() + " is added multiple times.");
          return;
        }

        types.put(message.getFullName(), message);
      }

      private final Set<String> files = new HashSet<>();
      private Map<String, Descriptor> types = new HashMap<>();
    }
  }

  /**
   * An interface for json formatting that can be used in
   * combination with the omittingInsignificantWhitespace() method
   */
  interface TextGenerator {
    void indent();

    void outdent();

    void print(final CharSequence text) throws IOException;
  }

  /**
   * Format the json without indentation
   */
  private static final class CompactTextGenerator implements TextGenerator {
    private final Appendable output;

    private CompactTextGenerator(final Appendable output) {
      this.output = output;
    }

    /**
     * ignored by compact printer
     */
    @Override
    public void indent() {}

    /**
     * ignored by compact printer
     */
    @Override
    public void outdent() {}

    /**
     * Print text to the output stream.
     */
    @Override
    public void print(final CharSequence text) throws IOException {
      output.append(text);
    }
  }
  /**
   * A TextGenerator adds indentation when writing formatted text.
   */
  private static final class PrettyTextGenerator implements TextGenerator {
    private final Appendable output;
    private final StringBuilder indent = new StringBuilder();
    private boolean atStartOfLine = true;

    private PrettyTextGenerator(final Appendable output) {
      this.output = output;
    }

    /**
     * Indent text by two spaces.  After calling Indent(), two spaces will be
     * inserted at the beginning of each line of text.  Indent() may be called
     * multiple times to produce deeper indents.
     */
    @Override
    public void indent() {
      indent.append("  ");
    }

    /**
     * Reduces the current indent level by two spaces, or crashes if the indent
     * level is zero.
     */
    @Override
    public void outdent() {
      final int length = indent.length();
      if (length < 2) {
        throw new IllegalArgumentException(" Outdent() without matching Indent().");
      }
      indent.delete(length - 2, length);
    }

    /**
     * Print text to the output stream.
     */
    @Override
    public void print(final CharSequence text) throws IOException {
      final int size = text.length();
      int pos = 0;

      for (int i = 0; i < size; i++) {
        if (text.charAt(i) == '\n') {
          write(text.subSequence(pos, i + 1));
          pos = i + 1;
          atStartOfLine = true;
        }
      }
      write(text.subSequence(pos, size));
    }

    private void write(final CharSequence data) throws IOException {
      if (data.length() == 0) {
        return;
      }
      if (atStartOfLine) {
        atStartOfLine = false;
        output.append(indent);
      }
      output.append(data);
    }
  }

  /**
   * A Printer converts protobuf messages to JSON format.
   */
  private static final class PrinterImpl {
    private final boolean includingDefaultValueFields;
    private final boolean preservingProtoFieldNames;
    private final TextGenerator generator;
    // We use Gson to help handle string escapes.
    private final Gson gson;
    private final CharSequence blankOrSpace;
    private final CharSequence blankOrNewLine;
    private final CamelCaser camelCaser = new CamelCaser();

    private static class GsonHolder {
      private static final Gson DEFAULT_GSON = new GsonBuilder().disableHtmlEscaping().create();
    }

    PrinterImpl(
        TypeRegistry registry,
        boolean includingDefaultValueFields,
        boolean preservingProtoFieldNames,
        Appendable jsonOutput,
        boolean omittingInsignificantWhitespace) {
      this.includingDefaultValueFields = includingDefaultValueFields;
      this.preservingProtoFieldNames = preservingProtoFieldNames;
      this.gson = GsonHolder.DEFAULT_GSON;
      // json format related properties, determined by printerType
      if (omittingInsignificantWhitespace) {
        this.generator = new CompactTextGenerator(jsonOutput);
        this.blankOrSpace = "";
        this.blankOrNewLine = "";
      } else {
        this.generator = new PrettyTextGenerator(jsonOutput);
        this.blankOrSpace = " ";
        this.blankOrNewLine = "\n";
      }
    }

    void print(MessageOrBuilder message) throws IOException {
      WellKnownTypePrinter specialPrinter =
          wellKnownTypePrinters.get(message.getDescriptorForType().getFullName());
      if (specialPrinter != null) {
        specialPrinter.print(this, message);
        return;
      }
      print(message, null);
    }

    private interface WellKnownTypePrinter {
      void print(PrinterImpl printer, MessageOrBuilder message) throws IOException;
    }

    private static final Map<String, WellKnownTypePrinter> wellKnownTypePrinters =
        buildWellKnownTypePrinters();

    private static Map<String, WellKnownTypePrinter> buildWellKnownTypePrinters() {
      Map<String, WellKnownTypePrinter> printers = new HashMap<>();
      return printers;
    }

    /** Prints a regular message with an optional type URL. */
    private void print(MessageOrBuilder message, String typeUrl) throws IOException {
      generator.print("{" + blankOrNewLine);
      generator.indent();

      boolean printedField = false;
      if (typeUrl != null) {
        generator.print("\"@type\":" + blankOrSpace + gson.toJson(typeUrl));
        printedField = true;
      }
      Map<FieldDescriptor, Object> fieldsToPrint = null;
      if (includingDefaultValueFields) {
        fieldsToPrint = new TreeMap<>();
        for (FieldDescriptor field : message.getDescriptorForType().getFields()) {
          if (field.isOptional()) {
            if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE
                && !message.hasField(field)){
              // Always skip empty optional message fields. If not we will recurse indefinitely if
              // a message has itself as a sub-field.
              continue;
            }
            OneofDescriptor oneof = field.getContainingOneof();
            if (oneof != null && !message.hasField(field)) {
                // Skip all oneof fields except the one that is actually set
              continue;
            }
          }
          fieldsToPrint.put(field, message.getField(field));
        }
      } else {
        fieldsToPrint = message.getAllFields();
      }
      for (Map.Entry<FieldDescriptor, Object> field : fieldsToPrint.entrySet()) {
        if (printedField) {
          // Add line-endings for the previous field.
          generator.print("," + blankOrNewLine);
        } else {
          printedField = true;
        }
        printField(field.getKey(), field.getValue());
      }

      // Add line-endings for the last field.
      if (printedField) {
        generator.print(blankOrNewLine);
      }
      generator.outdent();
      generator.print("}");
    }

    private void printField(FieldDescriptor field, Object value) throws IOException {
      String fieldName = camelCaser.getCamelCase(field.getName());
      if (!preservingProtoFieldNames && field.getOptions().hasExtension(Proto.jsonName)) {
        fieldName = field.getOptions().getExtension(Proto.jsonName);
      }
      generator.print("\"" + fieldName + "\" :" + blankOrSpace);
      if (field.isRepeated()) {
        printRepeatedFieldValue(field, value);
      } else {
        printSingleFieldValue(field, value);
      }
    }

    @SuppressWarnings("rawtypes")
    private void printRepeatedFieldValue(FieldDescriptor field, Object value) throws IOException {
      generator.print("[ ");  // KRW match format with space
      boolean printedElement = false;
      for (Object element : (List) value) {
        if (printedElement) {
          generator.print("," + blankOrSpace);
        } else {
          printedElement = true;
        }
        printSingleFieldValue(field, element);
      }
      if (printedElement) {
        generator.print(" ");  // KRW match format with space
      }
      generator.print("]");  // KRW match format with space
    }

    private void printSingleFieldValue(FieldDescriptor field, Object value) throws IOException {
      printSingleFieldValue(field, value, false);
    }

    /**
     * Prints a field's value in JSON format.
     *
     * @param alwaysWithQuotes whether to always add double-quotes to primitive
     *        types.
     */
    private void printSingleFieldValue(
        final FieldDescriptor field, final Object value, boolean alwaysWithQuotes)
        throws IOException {
      switch (field.getType()) {
        case INT32:
        case SINT32:
        case SFIXED32:
          if (alwaysWithQuotes) {
            generator.print("\"");
          }
          generator.print(((Integer) value).toString());
          if (alwaysWithQuotes) {
            generator.print("\"");
          }
          break;

        case INT64:
        case SINT64:
        case SFIXED64:
          generator.print("\"" + ((Long) value).toString() + "\"");
          break;

        case BOOL:
          if (alwaysWithQuotes) {
            generator.print("\"");
          }
          if (((Boolean) value).booleanValue()) {
            generator.print("true");
          } else {
            generator.print("false");
          }
          if (alwaysWithQuotes) {
            generator.print("\"");
          }
          break;

        case FLOAT:
          Float floatValue = (Float) value;
          if (floatValue.isNaN()) {
            generator.print("\"NaN\"");
          } else if (floatValue.isInfinite()) {
            if (floatValue < 0) {
              generator.print("\"-Infinity\"");
            } else {
              generator.print("\"Infinity\"");
            }
          } else {
            if (alwaysWithQuotes) {
              generator.print("\"");
            }
            generator.print(floatValue.toString());
            if (alwaysWithQuotes) {
              generator.print("\"");
            }
          }
          break;

        case DOUBLE:
          Double doubleValue = (Double) value;
          if (doubleValue.isNaN()) {
            generator.print("\"NaN\"");
          } else if (doubleValue.isInfinite()) {
            if (doubleValue < 0) {
              generator.print("\"-Infinity\"");
            } else {
              generator.print("\"Infinity\"");
            }
          } else {
            if (alwaysWithQuotes) {
              generator.print("\"");
            }
            generator.print(doubleValue.toString());
            if (alwaysWithQuotes) {
              generator.print("\"");
            }
          }
          break;

        case UINT32:
        case FIXED32:
          if (alwaysWithQuotes) {
            generator.print("\"");
          }
          generator.print(unsignedToString((Integer) value));
          if (alwaysWithQuotes) {
            generator.print("\"");
          }
          break;

        case UINT64:
        case FIXED64:
          generator.print("\"" + unsignedToString((Long) value) + "\"");
          break;

        case STRING:
          generator.print(gson.toJson(value));
          break;

        case BYTES:
          generator.print("\"");
          generator.print(BaseEncoding.base64().encode(((ByteString) value).toByteArray()));
          generator.print("\"");
          break;

        case ENUM:
          // Special-case google.protobuf.NullValue (it's an Enum).
          if (field.getEnumType().getFullName().equals("google.protobuf.NullValue")) {
            // No matter what value it contains, we always print it as "null".
            if (alwaysWithQuotes) {
              generator.print("\"");
            }
            generator.print("null");
            if (alwaysWithQuotes) {
              generator.print("\"");
            }
          } else {
            if (((EnumValueDescriptor) value).getIndex() == -1) {
              generator.print(String.valueOf(((EnumValueDescriptor) value).getNumber()));
            } else {
              generator.print("\"" + ((EnumValueDescriptor) value).getName() + "\"");
            }
          }
          break;

        case MESSAGE:
        case GROUP:
          print((Message) value);
          break;
      }
    }
  }

  /** Convert an unsigned 32-bit integer to a string. */
  private static String unsignedToString(final int value) {
    if (value >= 0) {
      return Integer.toString(value);
    } else {
      return Long.toString(value & 0x00000000FFFFFFFFL);
    }
  }

  /** Convert an unsigned 64-bit integer to a string. */
  private static String unsignedToString(final long value) {
    if (value >= 0) {
      return Long.toString(value);
    } else {
      // Pull off the most-significant bit so that BigInteger doesn't think
      // the number is negative, then set it again using setBit().
      return BigInteger.valueOf(value & Long.MAX_VALUE).setBit(Long.SIZE - 1).toString();
    }
  }

  private static class ParserImpl {
    private final JsonParser jsonParser;
    private final boolean ignoringUnknownFields;
    private final int recursionLimit;
    private int currentDepth;
    private final CamelCaser camelCaser = new CamelCaser();

    ParserImpl(TypeRegistry registry, boolean ignoreUnknownFields, int recursionLimit) {
      this.ignoringUnknownFields = ignoreUnknownFields;
      this.jsonParser = new JsonParser();
      this.recursionLimit = recursionLimit;
      this.currentDepth = 0;
    }

    void merge(Reader json, Message.Builder builder) throws IOException {
      JsonReader reader = new JsonReader(json);
      reader.setLenient(false);
      merge(jsonParser.parse(reader), builder);
    }

    void merge(String json, Message.Builder builder) throws InvalidProtocolBufferException {
      try {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(false);
        merge(jsonParser.parse(reader), builder);
      } catch (InvalidProtocolBufferException e) {
        throw e;
      } catch (Exception e) {
        // We convert all exceptions from JSON parsing to our own exceptions.
        throw new InvalidProtocolBufferException(e.getMessage());
      }
    }

    private interface WellKnownTypeParser {
      void merge(ParserImpl parser, JsonElement json, Message.Builder builder)
          throws InvalidProtocolBufferException;
    }

    private static final Map<String, WellKnownTypeParser> wellKnownTypeParsers =
        buildWellKnownTypeParsers();

    private static Map<String, WellKnownTypeParser> buildWellKnownTypeParsers() {
      Map<String, WellKnownTypeParser> parsers = new HashMap<>();
      return parsers;
    }

    private void merge(JsonElement json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      WellKnownTypeParser specialParser =
          wellKnownTypeParsers.get(builder.getDescriptorForType().getFullName());
      if (specialParser != null) {
        specialParser.merge(this, json, builder);
        return;
      }
      mergeMessage(json, builder, false);
    }

    // Maps from camel-case field names to FieldDescriptor.
    private final Map<Descriptor, Map<String, FieldDescriptor>> fieldNameMaps =
        new HashMap<>();

    private Map<String, FieldDescriptor> getFieldNameMap(Descriptor descriptor) {
      if (!fieldNameMaps.containsKey(descriptor)) {
        Map<String, FieldDescriptor> fieldNameMap = new HashMap<>();
        for (FieldDescriptor field : descriptor.getFields()) {
          fieldNameMap.put(camelCaser.getCamelCase(field.getName()), field);
          if (field.getOptions().hasExtension(Proto.jsonName)) {
            fieldNameMap.put(field.getOptions().getExtension(Proto.jsonName), field);
          }
        }
        fieldNameMaps.put(descriptor, fieldNameMap);
        return fieldNameMap;
      }
      return fieldNameMaps.get(descriptor);
    }

    private void mergeMessage(JsonElement json, Message.Builder builder, boolean skipTypeUrl)
        throws InvalidProtocolBufferException {
      if (!(json instanceof JsonObject)) {
        throw new InvalidProtocolBufferException("Expect message object but got: " + json);
      }
      JsonObject object = (JsonObject) json;
      Map<String, FieldDescriptor> fieldNameMap = getFieldNameMap(builder.getDescriptorForType());
      for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
        if (skipTypeUrl && entry.getKey().equals("@type")) {
          continue;
        }
        FieldDescriptor field = fieldNameMap.get(entry.getKey());
        if (field == null) {
          if (ignoringUnknownFields) {
            continue;
          }
          throw new InvalidProtocolBufferException(
              "Cannot find field: "
                  + entry.getKey()
                  + " in message "
                  + builder.getDescriptorForType().getFullName());
        }
        mergeField(field, entry.getValue(), builder);
      }
    }

    private void mergeField(FieldDescriptor field, JsonElement json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      if (field.isRepeated()) {
        if (builder.getRepeatedFieldCount(field) > 0) {
          throw new InvalidProtocolBufferException(
              "Field " + field.getFullName() + " has already been set.");
        }
      } else {
        if (builder.hasField(field)) {
          throw new InvalidProtocolBufferException(
              "Field " + field.getFullName() + " has already been set.");
        }
        if (field.getContainingOneof() != null
            && builder.getOneofFieldDescriptor(field.getContainingOneof()) != null) {
          FieldDescriptor other = builder.getOneofFieldDescriptor(field.getContainingOneof());
          throw new InvalidProtocolBufferException(
              "Cannot set field "
                  + field.getFullName()
                  + " because another field "
                  + other.getFullName()
                  + " belonging to the same oneof has already been set ");
        }
      }
      if (field.isRepeated() && json instanceof JsonNull) {
        // We allow "null" as value for all field types and treat it as if the
        // field is not present.
        return;
      }
      if (field.isRepeated()) {
        mergeRepeatedField(field, json, builder);
      } else {
        Object value = parseFieldValue(field, json, builder);
        if (value != null) {
          builder.setField(field, value);
        }
      }
    }

    private void mergeRepeatedField(
        FieldDescriptor field, JsonElement json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      if (!(json instanceof JsonArray)) {
        throw new InvalidProtocolBufferException("Expect an array but found: " + json);
      }
      JsonArray array = (JsonArray) json;
      for (int i = 0; i < array.size(); ++i) {
        Object value = parseFieldValue(field, array.get(i), builder);
        if (value == null) {
          throw new InvalidProtocolBufferException("Repeated field elements cannot be null");
        }
        builder.addRepeatedField(field, value);
      }
    }

    private int parseInt32(JsonElement json) throws InvalidProtocolBufferException {
      try {
        return Integer.parseInt(json.getAsString());
      } catch (Exception e) {
        // Fall through.
      }
      // JSON doesn't distinguish between integer values and floating point values so "1" and
      // "1.000" are treated as equal in JSON. For this reason we accept floating point values for
      // integer fields as well as long as it actually is an integer (i.e., round(value) == value).
      try {
        BigDecimal value = new BigDecimal(json.getAsString());
        return value.intValueExact();
      } catch (Exception e) {
        throw new InvalidProtocolBufferException("Not an int32 value: " + json);
      }
    }

    private long parseInt64(JsonElement json) throws InvalidProtocolBufferException {
      try {
        return Long.parseLong(json.getAsString());
      } catch (Exception e) {
        // Fall through.
      }
      // JSON doesn't distinguish between integer values and floating point values so "1" and
      // "1.000" are treated as equal in JSON. For this reason we accept floating point values for
      // integer fields as well as long as it actually is an integer (i.e., round(value) == value).
      try {
        BigDecimal value = new BigDecimal(json.getAsString());
        return value.longValueExact();
      } catch (Exception e) {
        throw new InvalidProtocolBufferException("Not an int32 value: " + json);
      }
    }

    private int parseUint32(JsonElement json) throws InvalidProtocolBufferException {
      try {
        long result = Long.parseLong(json.getAsString());
        if (result < 0 || result > 0xFFFFFFFFL) {
          throw new InvalidProtocolBufferException("Out of range uint32 value: " + json);
        }
        return (int) result;
      } catch (InvalidProtocolBufferException e) {
        throw e;
      } catch (Exception e) {
        // Fall through.
      }
      // JSON doesn't distinguish between integer values and floating point values so "1" and
      // "1.000" are treated as equal in JSON. For this reason we accept floating point values for
      // integer fields as well as long as it actually is an integer (i.e., round(value) == value).
      try {
        BigDecimal decimalValue = new BigDecimal(json.getAsString());
        BigInteger value = decimalValue.toBigIntegerExact();
        if (value.signum() < 0 || value.compareTo(new BigInteger("FFFFFFFF", 16)) > 0) {
          throw new InvalidProtocolBufferException("Out of range uint32 value: " + json);
        }
        return value.intValue();
      } catch (InvalidProtocolBufferException e) {
        throw e;
      } catch (Exception e) {
        throw new InvalidProtocolBufferException("Not an uint32 value: " + json);
      }
    }

    private static final BigInteger MAX_UINT64 = new BigInteger("FFFFFFFFFFFFFFFF", 16);

    private long parseUint64(JsonElement json) throws InvalidProtocolBufferException {
      try {
        BigDecimal decimalValue = new BigDecimal(json.getAsString());
        BigInteger value = decimalValue.toBigIntegerExact();
        if (value.compareTo(BigInteger.ZERO) < 0 || value.compareTo(MAX_UINT64) > 0) {
          throw new InvalidProtocolBufferException("Out of range uint64 value: " + json);
        }
        return value.longValue();
      } catch (InvalidProtocolBufferException e) {
        throw e;
      } catch (Exception e) {
        throw new InvalidProtocolBufferException("Not an uint64 value: " + json);
      }
    }

    private boolean parseBool(JsonElement json) throws InvalidProtocolBufferException {
      if (json.getAsString().equals("true")) {
        return true;
      }
      if (json.getAsString().equals("false")) {
        return false;
      }
      throw new InvalidProtocolBufferException("Invalid bool value: " + json);
    }

    private static final double EPSILON = 1e-6;

    private float parseFloat(JsonElement json) throws InvalidProtocolBufferException {
      if (json.getAsString().equals("NaN")) {
        return Float.NaN;
      } else if (json.getAsString().equals("Infinity")) {
        return Float.POSITIVE_INFINITY;
      } else if (json.getAsString().equals("-Infinity")) {
        return Float.NEGATIVE_INFINITY;
      }
      try {
        // We don't use Float.parseFloat() here because that function simply
        // accepts all double values. Here we parse the value into a Double
        // and do explicit range check on it.
        double value = Double.parseDouble(json.getAsString());
        // When a float value is printed, the printed value might be a little
        // larger or smaller due to precision loss. Here we need to add a bit
        // of tolerance when checking whether the float value is in range.
        if (value > Float.MAX_VALUE * (1.0 + EPSILON)
            || value < -Float.MAX_VALUE * (1.0 + EPSILON)) {
          throw new InvalidProtocolBufferException("Out of range float value: " + json);
        }
        return (float) value;
      } catch (InvalidProtocolBufferException e) {
        throw e;
      } catch (Exception e) {
        throw new InvalidProtocolBufferException("Not a float value: " + json);
      }
    }

    private static final BigDecimal MORE_THAN_ONE = new BigDecimal(String.valueOf(1.0 + EPSILON));
    // When a float value is printed, the printed value might be a little
    // larger or smaller due to precision loss. Here we need to add a bit
    // of tolerance when checking whether the float value is in range.
    private static final BigDecimal MAX_DOUBLE =
        new BigDecimal(String.valueOf(Double.MAX_VALUE)).multiply(MORE_THAN_ONE);
    private static final BigDecimal MIN_DOUBLE =
        new BigDecimal(String.valueOf(-Double.MAX_VALUE)).multiply(MORE_THAN_ONE);

    private double parseDouble(JsonElement json) throws InvalidProtocolBufferException {
      if (json.getAsString().equals("NaN")) {
        return Double.NaN;
      } else if (json.getAsString().equals("Infinity")) {
        return Double.POSITIVE_INFINITY;
      } else if (json.getAsString().equals("-Infinity")) {
        return Double.NEGATIVE_INFINITY;
      }
      try {
        // We don't use Double.parseDouble() here because that function simply
        // accepts all values. Here we parse the value into a BigDecimal and do
        // explicit range check on it.
        BigDecimal value = new BigDecimal(json.getAsString());
        if (value.compareTo(MAX_DOUBLE) > 0 || value.compareTo(MIN_DOUBLE) < 0) {
          throw new InvalidProtocolBufferException("Out of range double value: " + json);
        }
        return value.doubleValue();
      } catch (InvalidProtocolBufferException e) {
        throw e;
      } catch (Exception e) {
        throw new InvalidProtocolBufferException("Not an double value: " + json);
      }
    }

    private String parseString(JsonElement json) {
      return json.getAsString();
    }

    private ByteString parseBytes(JsonElement json) throws InvalidProtocolBufferException {
      return ByteString.copyFrom(BaseEncoding.base64().decode(json.getAsString()));
    }

    private EnumValueDescriptor parseEnum(EnumDescriptor enumDescriptor, JsonElement json)
        throws InvalidProtocolBufferException {
      String value = json.getAsString();
      EnumValueDescriptor result = enumDescriptor.findValueByName(value);
      if (result == null) {
        // Try to interpret the value as a number.
        try {
          int numericValue = parseInt32(json);
          result = enumDescriptor.findValueByNumber(numericValue);
        } catch (InvalidProtocolBufferException e) {
          // Fall through. This exception is about invalid int32 value we get from parseInt32() but
          // that's not the exception we want the user to see. Since result == null, we will throw
          // an exception later.
        }

        if (result == null) {
          throw new InvalidProtocolBufferException(
              "Invalid enum value: " + value + " for enum type: " + enumDescriptor.getFullName());
        }
      }
      return result;
    }

    private Object parseFieldValue(FieldDescriptor field, JsonElement json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      if (json instanceof JsonNull) {
        return null;
      }
      switch (field.getType()) {
        case INT32:
        case SINT32:
        case SFIXED32:
          return parseInt32(json);

        case INT64:
        case SINT64:
        case SFIXED64:
          return parseInt64(json);

        case BOOL:
          return parseBool(json);

        case FLOAT:
          return parseFloat(json);

        case DOUBLE:
          return parseDouble(json);

        case UINT32:
        case FIXED32:
          return parseUint32(json);

        case UINT64:
        case FIXED64:
          return parseUint64(json);

        case STRING:
          return parseString(json);

        case BYTES:
          return parseBytes(json);

        case ENUM:
          return parseEnum(field.getEnumType(), json);

        case MESSAGE:
        case GROUP:
          if (currentDepth >= recursionLimit) {
            throw new InvalidProtocolBufferException("Hit recursion limit.");
          }
          ++currentDepth;
          Message.Builder subBuilder = builder.newBuilderForField(field);
          merge(json, subBuilder);
          --currentDepth;
          return subBuilder.build();

        default:
          throw new InvalidProtocolBufferException("Invalid field type: " + field.getType());
      }
    }
  }
}
