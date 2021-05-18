import java.io.FileNotFoundException;

class Usable {

    private static int USER_CMD_MAX_SPLIT = 3;
    private static int GROUP_CMD_MAX_SPLIT = 5;

    public enum userCmdEnum {
        CONNECT,
        DISCONNECT,
        ERROR,
    }

    public static Object parseCommand(String cmd) throws Exception {
        String[] cmdArr = cmd.split(" ", 2);
        if (cmdArr.length == 2) {
            String header = cmdArr[0];
            String body = cmdArr[1];
            switch (header) {
                case "/user":
                    return userParser(body);
                case "/group":
                    return groupParser(body);
            }
        }
        return cmd;
    }

    private static Object groupParser(String body) throws FileNotFoundException {
        String[] bodyArr = body.split(" ", GROUP_CMD_MAX_SPLIT);
        String cmd = bodyArr[0];
        String rest = "";
        switch (cmd) {
            case "send":
                switch (bodyArr[1]){
                    case "text":
                        return new Message.GroupTextMessage(bodyArr[2], body.substring(11 + bodyArr[2].length()));
                    case "file":
                        return new Message.GroupFileMessage(bodyArr[2], body.substring(11 + bodyArr[2].length()));
                }
            case "create":
                return new Requests.CreateGroupRequest(bodyArr[1]);
            case "leave":
                return new Requests.LeaveGroupRequest(bodyArr[1]);
            case "user":
                switch (bodyArr[1]) {
                    case "invite":
                        return new Requests.InviteToGroupRequest(bodyArr[2], bodyArr[3]);
                    case "mute":
                        return new Requests.MuteUserInGroupRequest(bodyArr[2], bodyArr[3], Long.parseLong(bodyArr[4]));
                    case "unmute":
                        return new Requests.UnmuteUserInGroupRequest(bodyArr[2], bodyArr[3]);
                    case "remove":
                        return new Requests.RemoveFromGroupRequest(bodyArr[2], bodyArr[3]);
                }
            case "coadmin":
                rest = bodyArr[1];
                switch (rest){
                    case "add":
                        return new Requests.CoadminAddRequest(bodyArr[2], bodyArr[3]);
                    case "remove":
                        return new Requests.CoadminRemoveRequest(bodyArr[2], bodyArr[3]);
                }
        }
        return body;
    }

    private static Object userParser(String body) throws FileNotFoundException {
        String[] bodyArr = body.split(" ", USER_CMD_MAX_SPLIT);
        String cmd = bodyArr[0];
        switch (cmd) {
            case "connect":
                return new Requests.ConnectRequest(bodyArr[1]);
            case "disconnect":
                return new Requests.DisconnectRequest();
            case "text":
                return new Message.TextMsgUser(null, bodyArr[1], bodyArr[2]);
            case "file":
                return new Message.FileMsgUser(null, bodyArr[1], bodyArr[2]);
        }
        return body;
    }

}
