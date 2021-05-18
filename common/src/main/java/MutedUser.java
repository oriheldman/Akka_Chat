import akka.actor.Cancellable;

public class MutedUser {

    private String username;
    protected long startTime;
    protected long interval;
    Cancellable cancelable;

    public MutedUser(String username, long startTime, long interval, Cancellable cancelable) {
        this.username = username;
        this.startTime = startTime;
        this.interval = interval;
        this.cancelable = cancelable;
    }

}
