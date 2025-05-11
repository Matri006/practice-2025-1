import org.glassfish.grizzly.http.util.TimeStamp;

public class User {
    private Long userId;
    private String username;
    private TimeStamp registeredAt;
    private boolean subscribe;

    public void setUserId(Long userId) {this.userId = userId;}
    public void setUsername(String username) {this.username = username;}
    public void setRegisteredAt(TimeStamp registeredAt) {this.registeredAt = registeredAt;}
    public void setSubscribe() {this.subscribe = false;}

}
