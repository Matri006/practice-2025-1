import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CurrencyParser {
    public static Map<String, Double> parseRates(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(json);
        JsonNode valuteNode = rootNode.path("Valute");

        if (valuteNode.isMissingNode() || valuteNode.isEmpty()) {
            return new HashMap<>();
        }


        Map<String, Double> rates = new HashMap<>();
        valuteNode.fields().forEachRemaining(entry -> {
            String code = entry.getValue().path("CharCode").asText();
            double rate = entry.getValue().path("Value").asDouble();
            rates.put(code, rate);

        });
        rates.put("RUB", 1.0);

        return rates;
    }
}
