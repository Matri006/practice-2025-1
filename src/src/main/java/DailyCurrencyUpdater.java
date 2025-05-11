import java.sql.Connection;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DailyCurrencyUpdater {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final DbFunc dbFunc;


    public DailyCurrencyUpdater(DbFunc dbFunc) {
        this.dbFunc = dbFunc;
    }

    public void startDailyUpdate() {
        long initialDelay = calculateInitialDelay();
        scheduler.scheduleAtFixedRate(this::updateTask,
                initialDelay,
                24 * 60 * 60,
                TimeUnit.SECONDS);
    }

    public void updateTask() {
        try {
            String json = CbrApi.getDailyRates();
            Map<String, Double> rates = CurrencyParser.parseRates(json);
            dbFunc.updateRates(rates);
            System.out.println("Курсы обновлены: " + new Date());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long calculateInitialDelay() {
        Calendar now = Calendar.getInstance();
        Calendar nextRun = Calendar.getInstance();
        nextRun.set(Calendar.HOUR_OF_DAY, 12);
        nextRun.set(Calendar.MINUTE, 0);
        nextRun.set(Calendar.SECOND, 0);

        if (now.after(nextRun)) {
            nextRun.add(Calendar.DAY_OF_MONTH, 1);
        }

        return (nextRun.getTimeInMillis() - now.getTimeInMillis()) / 1000;
    }

    public void stop() {
        scheduler.shutdown();
    }
}
