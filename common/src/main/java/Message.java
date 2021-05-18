import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;

class Message {

    static abstract class AbstractUserMsg<T> implements Serializable {
        String sourceUserName;
        String targetUserName;
        T content;
    }

    static class TextMsgUser extends AbstractUserMsg<String>{
        TextMsgUser(String sourceName, String targetName, String message){
            this.sourceUserName = sourceName;
            this.targetUserName = targetName;
            this.content = message;
        }
    }

    static class FileMsgUser extends AbstractUserMsg<File>{
        FileMsgUser(String sourceName, String targetName, String filePath) throws FileNotFoundException {
            this.sourceUserName = sourceName;
            this.targetUserName = targetName;
            this.content = new File(filePath);
            if(!this.content.exists()){
                throw new FileNotFoundException(filePath);
            }
        }
    }

//    Group messages
    static abstract class AbstractGroupMsg<T> extends Requests.AbstractRequest implements Serializable {
        String targetName;
        T content;
    }

    static class GroupTextMessage extends AbstractGroupMsg<String> {

        GroupTextMessage(String groupName, String message) {
            targetName = groupName;
            content = message;
        }
    }

    static class GroupFileMessage extends AbstractGroupMsg<File> {
        String filePath;
        GroupFileMessage(String groupName, String path) throws FileNotFoundException {
            targetName = groupName;
            filePath = path;
            content = new File(filePath);
            if (!content.exists()) {
                throw new FileNotFoundException(path);
            }
        }
    }
}
