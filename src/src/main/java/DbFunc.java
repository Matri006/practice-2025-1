import io.github.cdimascio.dotenv.Dotenv;
import org.glassfish.grizzly.utils.Pair;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbFunc {
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
    public boolean register(Long userId, String username){
        if (userExists(userId)) {
            System.out.println("Пользователь уже существует");
            return false;
        }

        String sql = "INSERT INTO users (user_id, username, created_at, subscribe) VALUES (?, ?, ?, ?)";

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, username);
            statement.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            statement.setBoolean(4, false);

            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Ошибка регистрации: " + e.getMessage());
            return false;
        }
    }
    private boolean userExists(Long userId){
        String sql = "SELECT 1 FROM users WHERE user_id = ?";

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setLong(1, userId);
            ResultSet rs = statement.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("Ошибка проверки пользователя: " + e.getMessage());
            return false;
        }
    }
    public boolean subscribe(Long userId){
        String sql = "";
        if(!isSubscribe(userId)) sql = "UPDATE users SET subscribe = true WHERE user_id = ?";
        else {return false;}
        try(PreparedStatement statement = conn.prepareStatement(sql)){
            statement.setLong(1, userId);
            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Ошибка подписки: " + e.getMessage());
            return false;
        }
    }
    public boolean unsubscribe(Long userId){
        String sql = "";
        if(isSubscribe(userId)) sql = "UPDATE users SET subscribe = false WHERE user_id = ?";
        else {return false;}
        try(PreparedStatement statement = conn.prepareStatement(sql)){
            statement.setLong(1, userId);
            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Ошибка отписки: " + e.getMessage());
            return false;
        }
    }
    public boolean isSubscribe(Long userId){
        String sql = "SELECT subscribe FROM users WHERE user_id = ?";

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setLong(1, userId);
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                return rs.getBoolean("subscribe");
            } else {
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Ошибка проверки подписки для пользователя " + userId + ": " + e.getMessage());
            throw new RuntimeException("Database error", e);
        }
    }
    public Map<String, Double> getRates()  {
        String sql = "SELECT currency, rate FROM currency_rates";
        Map<String, Double> rates = new HashMap<>();


        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String currency = rs.getString("currency");
                double rate = rs.getDouble("rate");
                rates.put(currency, rate);

            }
        } catch (SQLException e){
            System.err.println("Ошибка при запросе из БД: " + e.getMessage());
        }


        return rates;
    }
    public void updateRates(Map<String, Double> rates) {
        String sql = "delete from currency_rates";
        System.err.println("Данные удалены");
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

    public Map<Long, String> getUsersWithFavorite(){
        String sql = "select user_id from users where subscribe = true";
        Map<Long, String> data = new HashMap<>();
        try(PreparedStatement stmt = conn.prepareStatement(sql)){
            ResultSet users = stmt.executeQuery();
            while (users.next()) {
                long userId = users.getLong("user_id");
                data.put(userId, "");
                String sql1 = "SELECT currency FROM favorite_currencies WHERE user_id = ?";
                try(PreparedStatement statement = conn.prepareStatement(sql1)){
                    statement.setLong(1, userId);
                    ResultSet rs = statement.executeQuery();
                    while(rs.next()){
                        String curr = rs.getString("currency");
                        String t = data.get(userId);
                        t += curr + " ";
                        data.put(userId, t);
                    }
                } catch (SQLException e){
                    System.err.println(e);
                }
            }

        } catch(SQLException e){
            System.err.println(e);
        }
        return data;
    }
    public void addToFavor(Long UserId, String currency_code){
        String sql = "INSERT INTO favorite_currencies (user_id, currency) VALUES (?, ?) ON CONFLICT (user_id, currency) DO NOTHING";
        try(PreparedStatement stmt = conn.prepareStatement(sql)){
            stmt.setLong(1, UserId);
            stmt.setString(2, currency_code);

            stmt.execute();
        } catch(SQLException e){
            System.err.println(e);
        }
    }

    public void setUserState(long chatId, boolean isAdding) throws SQLException {
        String sql = "INSERT INTO user_state (user_id, is_adding_currency) \n" +
                "VALUES (?, ?) \n" +
                "ON CONFLICT (user_id) \n" +
                "DO UPDATE SET is_adding_currency = EXCLUDED.is_adding_currency;";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setLong(1, chatId);
        stmt.setBoolean(2, isAdding);
        stmt.executeUpdate();
    }

    public boolean isUserAddingCurrency(long chatId) throws SQLException {
        String sql = "SELECT is_adding_currency FROM user_state WHERE user_id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setLong(1, chatId);
        ResultSet rs = stmt.executeQuery();
        return rs.next() && rs.getBoolean(1);
    }

    public List<String> getFav(Long userId){
        List<String> res = new ArrayList<>();
        String sql = "select * from favorite_currencies where user_id = ?";
        try(PreparedStatement stmt = conn.prepareStatement(sql)){
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            while(rs.next()){
                res.add(rs.getString("currency"));
            }
        } catch(SQLException e){
            System.err.println(e);
        }
        return res;
    }
    public void setDelState(Long userId, boolean isDel){
        String sql = "INSERT INTO user_del_state (user_id, is_del_currency) \n" +
                "VALUES (?, ?) \n" +
                "ON CONFLICT (user_id) \n" +
                "DO UPDATE SET is_del_currency = EXCLUDED.is_del_currency;";
        try(PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setBoolean(2, isDel);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }
    public boolean isUserDelCurrency(Long userId){
        String sql = "SELECT is_del_currency FROM user_del_state WHERE user_id = ?";
        try(PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getBoolean(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    public void delFav(Long userId, String curr){
        String sql = "DELETE FROM favorite_currencies WHERE user_id = ? AND currency = ?";
        try(PreparedStatement stmt = conn.prepareStatement(sql)){
            stmt.setLong(1, userId);
            stmt.setString(2, curr);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    public double getCurr(String curr){
        String sql = "select  rate from currency_rates where currency = ?";
        double res = 0.0;
        try(PreparedStatement stmt = conn.prepareStatement(sql)){
            stmt.setString(1, curr);
            ResultSet rs = stmt.executeQuery();
            if(rs.next())  return rs.getDouble("rate");
        } catch (SQLException e){
            System.err.println(e);
        }
        return res;
    }
    public void setConvStep(long userId, int step) {
        String sql = """
    INSERT INTO user_conv_state (user_id, step)
    VALUES (?, ?)
    ON CONFLICT (user_id)
    DO UPDATE SET step = EXCLUDED.step;
    """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setInt(2, step);
            stmt.executeUpdate();
            System.out.println("Шаг конвертации успешно обновлен для пользователя " + userId);
        } catch (SQLException e) {
            System.err.println("Ошибка при установке шага для пользователя " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }


    public int getConvStep(long userId) {
        String sql = "SELECT step FROM user_conv_state WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("step");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка при получении шага конвертации для пользователя " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }


    public void setFromCurrency(long userId, String curr) {
        String sql = """
    INSERT INTO user_conv_state (user_id, from_currency)
    VALUES (?, ?)
    ON CONFLICT (user_id)
    DO UPDATE SET from_currency = EXCLUDED.from_currency;
    """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setString(2, curr);
            stmt.executeUpdate();
            System.out.println("Валюта 'от' успешно установлена для пользователя " + userId);
        } catch (SQLException e) {
            System.err.println("Ошибка при установке валюты 'от' для пользователя " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setToCurrency(long userId, String curr) {
        String sql = """
    INSERT INTO user_conv_state (user_id, to_currency)
    VALUES (?, ?)
    ON CONFLICT (user_id)
    DO UPDATE SET to_currency = EXCLUDED.to_currency;
    """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setString(2, curr);
            stmt.executeUpdate();
            System.out.println("Валюта 'до' успешно установлена для пользователя " + userId);
        } catch (SQLException e) {
            System.err.println("Ошибка при установке валюты 'до' для пользователя " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }


    public String getFromCurrency(long userId) {
        String sql = "SELECT from_currency FROM user_conv_state WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("from_currency");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка при получении валюты 'от' для пользователя " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public String getToCurrency(long userId) {
        String sql = "SELECT to_currency FROM user_conv_state WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("to_currency");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка при получении валюты 'до' для пользователя " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }


    public void clearConvState(long userId) {
        String sql = "UPDATE user_conv_state SET step = 0, from_currency = NULL, to_currency = NULL WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.executeUpdate();
            System.out.println("Состояние конвертации успешно очищено для пользователя " + userId);
        } catch (SQLException e) {
            System.err.println("Ошибка при очистке состояния конвертации для пользователя " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isUserConvCurrency(long userId) {
        String sql = "SELECT step FROM user_conv_state WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int step = rs.getInt("step");
                return step > 0;
            }
        } catch (SQLException e) {
            System.err.println("Ошибка при проверке состояния конвертации для пользователя " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public void setConvertState(long userId, boolean state) {
        if (state) {
            String sql = """
        INSERT INTO user_conv_state (user_id, step)
        VALUES (?, 0)
        ON CONFLICT (user_id)
        DO UPDATE SET step = 0, from_currency = NULL, to_currency = NULL;
        """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                stmt.executeUpdate();
                System.out.println("Состояние конвертации установлено для пользователя " + userId);
            } catch (SQLException e) {
                System.err.println("Ошибка при установке состояния конвертации для пользователя " + userId + ": " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            clearConvState(userId);
        }
    }
    public Map<String, Double> getUpdate(Long userId){
        Map<String, Double> res = new HashMap<>();
        String sql = "select currency from favorite_currencies join rates on currency_rates.currency = favorite_currencies.currency";
        try(PreparedStatement stmt = conn.prepareStatement(sql)){
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            while(rs.next()){
                res.put(rs.getString("currency"), rs.getDouble("rates"));
            }

        } catch (SQLException e){
            System.err.println(e);
        }
        return res;
    }





}
