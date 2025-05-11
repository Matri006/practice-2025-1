## Шаг 1. Создание нового Maven проекта в IntelliJ IDEA
## Шаг 2. Подключение всех зависимостей в pom.xml
Откройте файл pom.xml и добавьте следующий код 
```xml
<dependencies>  
    <dependency>        <groupId>org.slf4j</groupId>  
        <artifactId>slf4j-simple</artifactId>  
        <version>2.0.7</version>  
    </dependency>    <dependency>        <groupId>io.github.cdimascio</groupId>  
        <artifactId>java-dotenv</artifactId>  
        <version>5.2.2</version>  
    </dependency>    <dependency>        <groupId>org.telegram</groupId>  
        <artifactId>telegrambots</artifactId>  
        <version>6.9.0</version>  
    </dependency>    <dependency>        <groupId>org.postgresql</groupId>  
        <artifactId>postgresql</artifactId>  
        <version>42.7.5</version>  
    </dependency>
    ```
Первая зависимость нужна для шифрования паролей.
Вторая зависимость нужна для взятия данных из .env, так как Java не поддерживает этот формат без использования специальных библиотек.
Третья зависимость - это непосредственно библиотека для создания бота.
Четвертая зависимость - это библиотека для работы с БД (я использую JDBC для PostgreSQL).
## Шаг 3. Создание нового бота в BotFather
В Telegram в поиске введите @BotFather, нажмите /start, затем введите команду /newbot, бот попросит вас ввести имя. Если оно корректно и никем не используется, бот выдаст вам токен для работы с Telegram API и предупредит о том, что токен не должен попасть в открытый доступ.
## Шаг 4. Создание БД в PostgreSQL
Далее нужно создать базу данных. Она будет использоваться для хранения пользователей, их избранных валют, самих валют и состояний. ERD диаграмму можно посмотреть ниже
![[ERD.png]]
## Шаг 5. Создание .env
Так как при создании бота нас предупредили, что токен не должен попасть в открытый доступ, нам нельзя его хранить в коде (так как код хранится на GitHub, скорее всего в открытом репозитории и кто угодно может посмотреть его и получить токен, а значит доступ к боту). В корне проекта создаем .env файл, в который записываем параметры в виде ИМЯ ПАРАМЕТРА = "значение параметра". Затем файл .env добавляем в .gitignore.
```Java
import io.github.cdimascio.dotenv.Dotenv; // подключение библиотеки для чтения .env файла в код
```
```Java
// пример использования библиотеки в коде.
Dotenv dotenv = Dotenv.configure().load();  
this.botToken = dotenv.get("TELEGRAM_BOT_TOKEN");  
this.botUsername = dotenv.get("TELEGRAM_USERNAME"); 
```

Теперь наши данные защищены от посторонних глаз.
## Шаг 6. Создание класса для отправки запросов в ЦБ.
Нужно создать отдельный класс, например CbrApi, и написать в нем следующий код
```Java
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
```

Здесь используется функция для отправки HTTP-запросов из стандартной библиотеки Java, поэтому никаких дополнительных зависимостей подключать в pom.xml не нужно.

## Шаг 7. Получение JSON-ответа
После отправки запроса мы должны получить ответ, для этого удобно будет создать отдельный класс, например CurrencyParser
```Java
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
```

Так как ответ возвращается без рубля, но в нашем боте мы должны иметь возможность конвертировать в рубли, мы его добавляем отдельно (курс рубля к рублю равен 1,0).

## Шаг 8. Кэширование курсов
Что бы не спамить ЦБ при каждом запросе пользователя, нам нужно сохранять полученные курсы в БД. Для этого создаем класс для работы с БД.

```Java
//Код для подключения к БД
private final Connection conn;  
public DbFunc(){  
    Dotenv dotenv = Dotenv.load();  
    conn = ConnectToDb(dotenv.get("DBNAME"), dotenv.get("POSTGRE_USERNAME"), dotenv.get("POSTGRE_PASSWORD") );  
}  
public Connection ConnectToDb(String dbname, String username, String password) {  
    Connection conn = null;  
    try{  
        conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/" + dbname, username, password);  
        if(conn != null) System.out.println("Connect to DB");  
        else System.out.println("Connection failed");  
  
    } catch (SQLException e){  
        System.out.print(e);  
    }  
    return conn;  
}
```

```Java
//Пример класса для обновления курсов валют в БД
public void updateRates(Map<String, Double> rates) {  
    String sql = "delete from currency_rates";  
    System.out.println("Данные удалены");  
    try( PreparedStatement statement = conn.prepareStatement(sql)){  
        statement.executeUpdate();  
    } catch (SQLException e) {  
        System.err.println(e);  
    }  
  
    sql = "INSERT INTO currency_rates (currency, rate, updated_at) VALUES (?, ?, CURRENT_DATE)";  
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {  
        for (Map.Entry<String, Double> entry : rates.entrySet()) {  
            stmt.setString(1, entry.getKey());  
            stmt.setDouble(2, entry.getValue());  
            stmt.addBatch();  
        }  
        stmt.executeBatch();  
    } catch (SQLException e){  
        System.err.println(e);  
    }  
}
```

## Шаг 9. Создание класса бота

Создаем класс и наследуем его от LongPollingBot. В конструкторе класса с помощью токена подключаемся к TelegramAPI и к нашей БД.
Что бы наследование не вызывало ошибок, там нужно реализовать 3 метода: onUpdateReceived(Update update), getBotUsername(), getBotToken(). Если все выполнено верно, ошибки должны исчезнуть.

```Java
//Подключение бота
private final String botUsername;  
private final String botToken;  
private final DbFunc db;  
  
public ConvertBot(){  
    Dotenv dotenv = Dotenv.load();  
    this.botToken = dotenv.get("TELEGRAM_BOT_TOKEN");  
    this.botUsername = dotenv.get("TELEGRAM_USERNAME");  
    db = new DbFunc();
    }
```

```Java
//Полностью написанный метод обработки команд для моего бота
@Override  
public void onUpdateReceived(Update update) {  
    if (update.hasMessage() && update.getMessage().hasText()) {  
        Message message = update.getMessage();  
        Long chatId = update.getMessage().getChatId();  
        String userMessage = update.getMessage().getText();  
        SendMessage response = new SendMessage();  
        response.setChatId(chatId);  
        try {  
            if (userMessage.startsWith("/")) {  
                db.setUserState(chatId, false);  
                db.setDelState(chatId, false);  
                String command = userMessage.split(" ")[0].toLowerCase();  
                switch (command) {  
                    case "/start":  
                        response.setReplyMarkup(hideKeyboard());  
                        response.setText("Здравствуйте, используйте команды из меню для работы с ботом");  
  
                        registerUser(message);  
                        break;  
                    case "/list":  
                        response.setReplyMarkup(hideKeyboard());  
                        response.setText("Список доступных валют: \n" + getRates());  
                        break;  
  
                    case "/help":  
                        response.setReplyMarkup(hideKeyboard());  
                        response.setText("Для работы с ботом используйте команды из меню команд");  
                        break;  
                    case "/subscribe":  
                        response.setReplyMarkup(hideKeyboard());  
                        subscribeUser(message);  
                        response.setText("Вы подписаны на ежедневные обновления!\n Для получения ежедневных обновлений о курсах валют добавьте валюты в избранные");  
                        break;  
                    case "/unsubscribe":  
                        response.setReplyMarkup(hideKeyboard());  
                        unsubscribeUser(message);  
                        response.setText("Вы отписаны от ежедневных обновлений!");  
                        break;  
                    case "/convert":  
                        response.setText("Выберите валюту, из которой вы хотите конвертировать:");  
                        response.setReplyMarkup(createRowKeyBoard());  
                        db.setConvertState(chatId, true);  
                        db.setConvStep(chatId, 1);  
  
                        break;  
                    case "/add":  
                        if(db.isSubscribe(chatId)) {  
                            response.setText("Выберите валюту:");  
                            response.setReplyMarkup(createRowKeyBoard());  
  
                            try {  
                                db.setUserState(chatId, true);  
                            } catch (SQLException e) {  
                                throw new RuntimeException(e);  
                            }  
                        } else response.setText("Подпишитесь для добавления валют в избранные");  
                        break;  
                    case "/delete":  
                        if(db.isSubscribe(chatId)) {  
                            if (isFavorEmpty(message)) response.setText("У вас нет избранных валют");  
                            else {  
                                response.setText("Выберите валюту");  
                                response.setReplyMarkup(delRowKeyBoard(message));  
                                db.setDelState(chatId, true);  
                            }  
                        } else response.setText("подпишитесь для просмотра избранных валют");  
  
                        break;  
                    case "/favorite":  
                        response.setReplyMarkup(hideKeyboard());  
                        response.setText(getFavor(message));  
                        break;  
                    case "/status":  
                        response.setReplyMarkup(hideKeyboard());  
                        if(db.isSubscribe(chatId)) response.setText("Вы подписаны на ежедневные обновления!");  
                        else response.setText("Вы не подписаны на ежедневные обновления!");  
                        break;  
  
  
  
                    default: response.setText("Неизвестная команда");  
                }  
            } else if (db.isUserAddingCurrency(chatId)) {  
                if (isValidCurr(userMessage)) {  
                    addFavor(message);  
                    response.setText("Валюта " + userMessage + " добавлена в избранные");  
                } else {  
                    response.setText("Такой валюты не существует");  
                }  
            } else if(db.isUserDelCurrency(chatId)){  
                if(isValidCurr(userMessage)){  
                    delFav(message);  
                    response.setText("Валюта " + userMessage + " удалена из избранных");  
                    response.setReplyMarkup(delRowKeyBoard(message));  
                } else {  
                    response.setText("Такой валюты не существует");  
                }  
            } else if (db.isUserConvCurrency(chatId)) {  
                int step = db.getConvStep(chatId);  
  
                switch (step) {  
                    case 1:  
                        if (isValidCurr(userMessage)) {  
                            db.setFromCurrency(chatId, userMessage.toUpperCase());  
                            db.setConvStep(chatId, 2);  
                            response.setText("Теперь выберите валюту, в которую хотите конвертировать:");  
                            response.setReplyMarkup(createRowKeyBoard());  
                        } else {  
                            response.setText("Неверная валюта. Попробуйте снова.");  
                        }  
                        break;  
  
                    case 2:  
                        if (isValidCurr(userMessage)) {  
                            db.setToCurrency(chatId, userMessage.toUpperCase());  
                            db.setConvStep(chatId, 3);  
                            response.setReplyMarkup(hideKeyboard());  
                            response.setText("Введите сумму для конвертации:");  
                        } else {  
                            response.setText("Неверная валюта. Попробуйте снова.");  
                        }  
                        break;  
  
                    case 3:  
                        try {  
                            double amount = Double.parseDouble(userMessage);  
                            String from = db.getFromCurrency(chatId);  
                            String to = db.getToCurrency(chatId);  
                            double result = getConv(from, to, amount);  
  
                            response.setText(String.format("%.2f %s = %.2f %s", amount, from, result, to));  
  
                            db.clearConvState(chatId);  
                            db.setConvertState(chatId, false);  
  
                        } catch (NumberFormatException e) {  
                            response.setText("Введите корректную сумму (например, 123.45)");  
                        }  
                        break;  
                    default:  
                        response.setText("Что-то пошло не так. Конвертация сброшена. Попробуйте снова с команды /convert.");  
                        db.clearConvState(chatId);  
                        db.setConvertState(chatId, false);  
                        response.setReplyMarkup(hideKeyboard());  
                        break;  
                }  
            }  
  
        } catch (SQLException e){  
            System.err.println(e);  
        }  
        try {  
            if (response.getText() == null || response.getText().isEmpty()) {  
                response.setText("Произошла ошибка. Попробуйте снова или введите /help.");  
            }  
            execute(response);  
        } catch (TelegramApiException e) {  
            e.printStackTrace();  
        }  
    }  
}
```

```Java
//методы getBotUsername и getBotToken
@Override  
public String getBotUsername() {  
    return this.botUsername;  
}  
  
@Override  
public String getBotToken() {  
    return this.botToken;  
}
```

На диаграмме ниже наглядно показана обработка команд, которую реализует метод OnUpdatedReceived
![[диаграмма компонентов.png]]
## Шаг 10. Создание класса Main
Что бы наш бот запустился, нам нужен метод для запуска. Создайте класс Main и вставьте в него следующий код:
```Java
import org.telegram.telegrambots.meta.TelegramBotsApi;  
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;  
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;  
  
public class Main {  
    public static void main(String[] args){  
        try {  
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);  
            botsApi.registerBot(new ConvertBot());  
        } catch (TelegramApiException e) {  
            e.printStackTrace();  
        }  
  
    }  
}
```

## Шаг 11. Реализуйте функционал своего бота
На картинке ниже представлена диаграмма классов для моего бота, на которой видны все функции и методы классов и то, как каждый класс взаимодействует между собой.
![[диаграмма классов.png]]