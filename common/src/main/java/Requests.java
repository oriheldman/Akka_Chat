import java.io.Serializable;

class Requests {
    static abstract class AbstractRequest implements Serializable{
        String sourceUserName;
    }

    static class GetActorRequest implements Serializable{
        String username;

        GetActorRequest(String username){
            this.username = username;
        }
    }

    static class ConnectRequest implements Serializable{
        String username;

        ConnectRequest(String username){
            this.username = username;
        }
    }

    static class DisconnectRequest implements Serializable{
        String username;

        DisconnectRequest(){
        }
    }

    static class TextRequest implements Serializable{
        String message;
        String source;

        TextRequest(String message){
            this.message = message;
        }
    }

//    Group Request

    static class CreateGroupRequest implements Serializable{
        String groupName;
        String adminName;

        public CreateGroupRequest(String groupName) {
            this.groupName = groupName;
        }
    }

    static class LeaveGroupRequest implements Serializable{
        String groupName;
        String userName;

        public LeaveGroupRequest(String groupName) {
            this.groupName = groupName;
        }
    }

    static abstract class GroupAdminRequest extends AbstractRequest{
        String groupName;
        String targetUserName;
    }

    static class InviteToGroupRequest extends GroupAdminRequest{

        public InviteToGroupRequest(String groupName, String targetUserName) {
            this.groupName = groupName;
            this.targetUserName = targetUserName;
        }
        public InviteToGroupRequest(String groupName, String targetUserName, String sourceUserName) {
            this.groupName = groupName;
            this.targetUserName = targetUserName;
            this.sourceUserName = sourceUserName;
        }
    }

    static class RemoveFromGroupRequest extends GroupAdminRequest{

        public RemoveFromGroupRequest(String groupName, String targetUserName) {
            this.groupName = groupName;
            this.targetUserName = targetUserName;
        }

    }

    static class MuteUserInGroupRequest extends GroupAdminRequest{
        long timeSec;

        public MuteUserInGroupRequest(String groupName, String targetUserName, long timeSec) {
            this.groupName = groupName;
            this.targetUserName = targetUserName;
            this.timeSec = timeSec;
        }
    }

    static class UnmuteUserInGroupRequest extends GroupAdminRequest{

        public UnmuteUserInGroupRequest(String groupName, String targetUserName) {
            this.groupName = groupName;
            this.targetUserName = targetUserName;
        }
    }

    static class CoadminAddRequest extends GroupAdminRequest{

        public CoadminAddRequest(String groupName, String targetUserName) {
            this.groupName = groupName;
            this.targetUserName = targetUserName;
        }
    }

    static class CoadminRemoveRequest extends GroupAdminRequest{

        public CoadminRemoveRequest(String groupName, String targetUserName) {
            this.groupName = groupName;
            this.targetUserName = targetUserName;
        }
    }

    static class AddUserToGroup implements Serializable {
        String group;
        String user;
        // ctor
        AddUserToGroup(String group, String user) {
            this.group = group;
            this.user = user;
        }
    }
}
