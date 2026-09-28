package com.axel.pennywise.domain.book;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class BookContractTest {
  @SuppressWarnings("unchecked")
  Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  Map<String, Object> spec() throws Exception {
    try (var stream = getClass().getResourceAsStream("/openapi/pennywise-v1.yaml")) {
      return new Yaml().load(stream);
    }
  }

  @Test
  void stylesAndDefaultsMatchBackend() throws Exception {
    var schemas = map(map(spec().get("components")).get("schemas"));
    assertEquals(BookStyles.ICONS, map(schemas.get("BookIcon")).get("enum"));
    assertEquals(BookStyles.COLORS, map(schemas.get("BookColor")).get("enum"));
    assertEquals(BookStyles.DEFAULT_ICON, map(schemas.get("BookIcon")).get("default"));
    assertEquals(BookStyles.DEFAULT_COLOR, map(schemas.get("BookColor")).get("default"));
    var props = map(map(schemas.get("BookResponse")).get("properties"));
    assertEquals("#/components/schemas/BookIcon", map(props.get("icon")).get("$ref"));
    assertEquals("#/components/schemas/BookColor", map(props.get("color")).get("$ref"));
  }

  @Test
  void requestStylesAcceptExplicitNull() throws Exception {
    var schemas = map(map(spec().get("components")).get("schemas"));
    var icons = new ArrayList<Object>(BookStyles.ICONS);
    icons.add(null);
    var colors = new ArrayList<Object>(BookStyles.COLORS);
    colors.add(null);
    assertEquals(icons, map(schemas.get("BookIconInput")).get("enum"));
    assertEquals(colors, map(schemas.get("BookColorInput")).get("enum"));
    assertEquals(true, map(schemas.get("BookIconInput")).get("nullable"));
    assertEquals(true, map(schemas.get("BookColorInput")).get("nullable"));
    for (String name : List.of("BookCreateRequest", "BookUpdateRequest")) {
      var props = map(map(schemas.get(name)).get("properties"));
      assertEquals("#/components/schemas/BookIconInput", map(props.get("icon")).get("$ref"));
      assertEquals("#/components/schemas/BookColorInput", map(props.get("color")).get("$ref"));
    }
    var update = map(map(schemas.get("BookUpdateRequest")).get("properties"));
    assertEquals(true, map(update.get("name")).get("nullable"));
  }

  @Test
  void frozenFieldsOrderAndLimits() throws Exception {
    var spec = spec();
    var schemas = map(map(spec.get("components")).get("schemas"));
    var props = map(map(schemas.get("BookResponse")).get("properties"));
    for (String field : List.of("icon", "color", "sortOrder", "balanceMinor"))
      assertTrue(props.containsKey(field));
    var create = map(map(schemas.get("BookCreateRequest")).get("properties"));
    assertEquals(80, map(create.get("name")).get("maxLength"));
    assertEquals(3, map(create.get("currencyCode")).get("minLength"));
    assertEquals(3, map(create.get("currencyCode")).get("maxLength"));
    assertFalse(map(create.get("currencyCode")).containsKey("enum"));
    assertEquals("int64", map(create.get("openingBalanceMinor")).get("format"));
    assertFalse(map(create.get("openingBalanceMinor")).containsKey("maximum"));
    assertTrue(
        map(create.get("openingBalanceMinor"))
            .get("description")
            .toString()
            .contains("9007199254740991"));
    assertEquals(List.of("bookIds"), map(schemas.get("BookOrderRequest")).get("required"));
    var paths = map(spec.get("paths"));
    var order = map(map(paths.get("/v1/books/order")).get("put"));
    assertTrue(map(order.get("responses")).containsKey("204"));
    assertTrue(order.get("description").toString().contains("VALIDATION_ERROR"));
    assertTrue(order.get("description").toString().contains("NOT_FOUND"));
    var deletion = map(map(paths.get("/v1/books/{bookId}")).get("delete"));
    assertTrue(deletion.get("description").toString().contains("LAST_BOOK_REQUIRED"));
    assertTrue(deletion.get("description").toString().contains("no automatic purge"));
    assertTrue(
        map(map(paths.get("/v1/books")).get("post"))
            .get("description")
            .toString()
            .contains("BOOK_LIMIT_REACHED"));
  }
}
