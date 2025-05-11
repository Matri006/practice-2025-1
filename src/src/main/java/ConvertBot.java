import io.github.cdimascio.dotenv.Dotenv;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConvertBot extends TelegramLongPollingBot {
    private final String botUsername;
    private final String botToken;
    private final DbFunc db;

    public ConvertBot(){
        Dotenv dotenv = Dotenv.load();
        this.botToken = dotenv.get("TELEGRAM_BOT_TOKEN");
        this.botUsername = dotenv.get("TELEGRAM_USERNAME");
        db = new DbFunc();


        CreateCommands();
        new Thread(() -> {
            try {
              //new DailyCurrencyUpdater(db).updateTask();
                DailyCurrencyUpdater updater = new DailyCurrencyUpdater(db);
                updater.startDailyUpdate();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        new Thread(()->{
            try {
                Notifier notifier = new Notifier(db, this);
                notifier.startDailyNotifier();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

    }
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
    @Override
    public String getBotUsername() {
        return this.botUsername;
    }

    @Override
    public String getBotToken() {
        return this.botToken;
    }
    private void CreateCommands(){
        List<BotCommand> listOfBotCommand = new ArrayList<>();
//        listOfBotCommand.add(new BotCommand("/start", "начало работы"));
        listOfBotCommand.add(new BotCommand("/convert", "конвертировать валюту"));
        listOfBotCommand.add(new BotCommand("/list", "список доступных валют"));
        listOfBotCommand.add(new BotCommand("/subscribe", "подписка на ежедневные сообщения о текущих курсах валют"));
        listOfBotCommand.add(new BotCommand("/unsubscribe", "отписка от ежедневных сообщений о текущих курсах валют"));
        listOfBotCommand.add(new BotCommand("/favorite", "просмотреть избранные валюты"));
        listOfBotCommand.add(new BotCommand("/add", "добавление валюты в список избранных"));
        listOfBotCommand.add(new BotCommand("/delete", "удаление валюты из списка избранных"));
        listOfBotCommand.add(new BotCommand("/status", "проверит статус подписки"));
        listOfBotCommand.add(new BotCommand("/help", "помощь"));
        try{
            execute(new SetMyCommands(listOfBotCommand, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e){
            e.printStackTrace();
        }
    }

    private void registerUser(Message message) {
        Long userId = message.getFrom().getId();
        String username = message.getFrom().getUserName();
            db.register(userId, username);





    }
    private void subscribeUser(Message message){
        Long userId = message.getFrom().getId();
        db.subscribe(userId);
    }
    private void unsubscribeUser(Message message){
        Long userId = message.getFrom().getId();
        db.unsubscribe(userId);
    }
    private String getRates(){
        Map<String, Double> rates = db.getRates();

        if(rates.isEmpty()) {
            return "Валют нет";
        } else {
            String s = "";
            for(Map.Entry<String, Double> entry: rates.entrySet()){
                s += entry.getKey() + "     " + entry.getValue() + "\n";
            }
            return s;
        }
    }
    private String getFavor(Message message){
        Long id = message.getFrom().getId();
        String s = "";
        if(db.isSubscribe(id)) {
            Map<Long, String> data = db.getUsersWithFavorite();
            for (Map.Entry<Long, String> entry : data.entrySet()) {
                if (id.equals(entry.getKey())) {
                    if (entry.getValue().isEmpty()) {
                        s =  "Ничего не нашлось(";
                        break;
                    }
                    else {

                        s += "Ваши избранные валюты\n";
                        for (String curr : entry.getValue().split(" ")) {
                            s += curr + "\n";
                        }
                    }
                }
            }
            return s;

        } else return "Подпишитесь, что бы выбирать избранные валюты";
    }
    private void addFavor(Message message){
        Long userId = message.getFrom().getId();
        db.addToFavor(userId, message.getText());
    }
    private ReplyKeyboardMarkup createRowKeyBoard(){
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> rowList = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        Map<String, Double> rates = db.getRates();
        int i = 0;
        for(Map.Entry<String, Double> entry: rates.entrySet()){
            row.add(entry.getKey());
            i++;
            if(i >= 3){
                rowList.add(row);
                row = new KeyboardRow();
                i=0;
            }
        }
        if (!row.isEmpty()) {
            rowList.add(row);
        }


        keyboardMarkup.setKeyboard(rowList);
        return keyboardMarkup;
    }
    public boolean isValidCurr(String name){
        return db.getRates().containsKey(name);
    }

    private ReplyKeyboardMarkup delRowKeyBoard(Message message){
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> rowList = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        List<String> rates = db.getFav(message.getFrom().getId());
        int i = 0;
            for (String s : rates) {

                row.add(s);
                i++;
                if (i >= 3) {
                    rowList.add(row);
                    row = new KeyboardRow();
                    i = 0;
                }
            }
            if (!row.isEmpty()) {
                rowList.add(row);
            }



        keyboardMarkup.setKeyboard(rowList);
        return keyboardMarkup;
    }
    private boolean isFavorEmpty(Message message){
        return db.getFav(message.getFrom().getId()).isEmpty();
    }
    private void delFav(Message message){
        Long userId = message.getFrom().getId();
        db.delFav(userId, message.getText());
    }
    private Double getConv(String a, String b, double sum){
        double count_a = db.getCurr(a);
        sum = sum * count_a;
        double count_b = db.getCurr(b);
        return (double)(sum /count_b);
    }
    private ReplyKeyboardRemove hideKeyboard(){
        ReplyKeyboardRemove keyboardRemove = new ReplyKeyboardRemove();
        keyboardRemove.setRemoveKeyboard(true);
        return keyboardRemove;
    }



}
