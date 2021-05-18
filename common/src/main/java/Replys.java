import java.io.Serializable;

class Replys {

    static abstract class AbstractReply implements Serializable {
        String sender;
    }

    static class ConnectReply implements Serializable{
        boolean isConnected;
        // ctor
        ConnectReply(boolean isConnected){
            this.isConnected = isConnected;
        }
    }

    static class DisconnectReply implements Serializable{
        boolean isDisconnected;
        // ctor
        DisconnectReply(boolean isDisconnected){
            this.isDisconnected = isDisconnected;
        }
    }
}
