import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class CbrApi {
    private static final String CBR_DAILY_URL = "https://www.cbr-xml-daily.ru/daily_json.js";
    public static String getDailyRates() throws IOException {
        URL url = new URL(CBR_DAILY_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        try (Scanner scanner = new Scanner(conn.getInputStream())) {
            scanner.useDelimiter("\\A");
            String json = scanner.hasNext() ? scanner.next() : "";
            System.out.println("==== Получен JSON от ЦБ ====");
            System.out.println(json);

            return json;
        }
    }
}
