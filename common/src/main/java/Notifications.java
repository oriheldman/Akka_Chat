import java.io.Serializable;

class Notifications {

    static abstract class AbstractNotification implements Serializable{
        String groupName;
        String targetUser;
        String sourceUserName;
    }

    static class MuteTimedUpNotification extends AbstractNotification {
        MuteTimedUpNotification(String group) {
            this.groupName = group;
        }
    }

    static class UnmuteNotification extends AbstractNotification {
        UnmuteNotification(String group, String sourceUserName) {
            this.groupName = group;
            this.sourceUserName = sourceUserName;
        }
    }

    static class MuteNotification extends AbstractNotification {
        long timeSec;
        MuteNotification(String group, long timeSec, String sourceUserName) {
            this.groupName = group;
            this.timeSec = timeSec;
            this.sourceUserName = sourceUserName;
        }
    }

    static class RemovedNotification extends AbstractNotification {
        RemovedNotification(String groupName, String userToRemove, String sourceUser){
            this.groupName = groupName;
            this.targetUser = userToRemove;
            this.sourceUserName = sourceUser;
        }
    }

    static class CoadminAddNotification extends AbstractNotification {
        CoadminAddNotification(String groupName){
            this.groupName = groupName;
        }
    }
    static class CoadminRemoveNotification extends AbstractNotification {
        CoadminRemoveNotification(String groupName){
            this.groupName = groupName;
        }
    }

    static class InvitationNotification extends AbstractNotification {
        Boolean approved;
        // ctor
        InvitationNotification(Boolean approved, String sourceUserName, String groupName, String targetUserName) {
            this.approved = approved;
            this.sourceUserName = sourceUserName;
            this.groupName = groupName;
            this.targetUser = targetUserName;
        }
    }

    static class GroupBroadcast implements Serializable {
        String message;
        GroupBroadcast(String message) {
            this.message = message;
        }
    }
}
