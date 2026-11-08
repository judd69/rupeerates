package rupeerates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** JSON parser: the shapes Frankfurter actually returns. */
class JsonTest {

    @Test
    void parsesLatestResponse() {
        Object parsed = Json.parse(
                "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-11-06\","
                        + "\"rates\":{\"INR\":93.92,\"EUR\":0.89566}}");
        Map<String, Object> root = Json.obj(parsed);
        assertEquals("USD", Json.str(Json.field(root, "base")));
        assertEquals("2026-11-06", Json.str(Json.field(root, "date")));
        assertEquals(93.92, Json.num(Json.obj(Json.field(root, "rates")).get("INR")));
    }

    @Test
    void parsesTimeseriesResponse() {
        Object parsed = Json.parse(
                "{\"base\":\"USD\",\"start_date\":\"2026-10-09\",\"end_date\":\"2026-11-06\","
                        + "\"rates\":{\"2026-10-09\":{\"INR\":96.73},\"2026-11-06\":{\"INR\":93.92}}}");
        Map<String, Object> root = Json.obj(parsed);
        Map<String, Object> rates = Json.obj(Json.field(root, "rates"));
        assertEquals(96.73, Json.num(Json.obj(rates.get("2026-10-09")).get("INR")));
        assertEquals(93.92, Json.num(Json.obj(rates.get("2026-11-06")).get("INR")));
    }

    @Test
    void rejectsTrailingData() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} extra"));
    }

    @Test
    void rejectsUnterminatedString() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"abc"));
    }

    @Test
    void escapesStringsForJsonOutput() {
        assertEquals("a\\\"b", Json.escape("a\"b"));
        assertEquals("a\\nb", Json.escape("a\nb"));
    }

    @Test
    void parsesNestedArrays() {
        Object parsed = Json.parse("[1, true, null, \"x\"]");
        List<?> list = (List<?>) parsed;
        assertEquals(4, list.size());
        assertEquals(1.0, list.get(0));
        assertEquals(Boolean.TRUE, list.get(1));
    }
}
