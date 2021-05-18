
import akka.actor.*;
import akka.routing.Broadcast;
import akka.routing.Router;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.time.Duration;
import java.util.HashMap;

public class Server extends AbstractActor {

    // users list mapping to their actors
    private HashMap<String, ActorRef> usersMap = new HashMap<>();
    // group list mapping to their actors
    private HashMap<String, Group> groupMap = new HashMap<>();

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                // incoming user
                .match(Message.AbstractGroupMsg.class, this::groupMessageHandler)
                .match(Requests.ConnectRequest.class, this::connectRequestHandler)
                .match(Requests.DisconnectRequest.class, this::disconnectHandler)
                .match(Requests.CreateGroupRequest.class, this::createGroupRequestHandler)
                .match(Requests.LeaveGroupRequest.class, this::leaveGroupRequestHandler)
                .match(Requests.GetActorRequest.class, this::getActorRequestHandler)
                .match(Requests.AddUserToGroup.class, this::addUserToGroup)
                // incoming admin
                .match(Requests.InviteToGroupRequest.class, this::groupAdminRequestsHandler)
                .match(Requests.RemoveFromGroupRequest.class, this::groupAdminRequestsHandler)
                .match(Requests.MuteUserInGroupRequest.class, this::groupAdminRequestsHandler)
                .match(Requests.UnmuteUserInGroupRequest.class, this::groupAdminRequestsHandler)
                .match(Requests.CoadminAddRequest.class, this::groupAdminRequestsHandler)
                .match(Requests.CoadminRemoveRequest.class, this::groupAdminRequestsHandler)
                .build();
    }

    private void addUserToGroup(Requests.AddUserToGroup req) {
        Group group = groupMap.get(req.group);
        group.getUsers().add(req.user);
        group.router = group.getRouter().addRoutee(usersMap.get(req.user));
    }

    private void groupAdminRequestsHandler(Requests.GroupAdminRequest req){
        String groupName = req.groupName;
        String sourceUserName = req.sourceUserName;
        String targetUserName = req.targetUserName;
        Group group = groupMap.get(groupName);
        if (group == null) {
            sender().tell(groupName + " does not exist!", self());
        } else if (!usersMap.containsKey(targetUserName)) {
            sender().tell(targetUserName + " does not exist!", self());
        } else if (!group.getAdmin().equals(sourceUserName) && !group.getCoAdmins().contains(sourceUserName)) {
            sender().tell(String.format("You are neither an admin nor a co-admin of %s!", groupName), self());
        } else {
            if (req instanceof Requests.InviteToGroupRequest)
                finallyInviteTask((Requests.InviteToGroupRequest)req, targetUserName, sourceUserName);
            else if (req instanceof Requests.RemoveFromGroupRequest)
                finallyRemoveTask(group, (Requests.RemoveFromGroupRequest)req, targetUserName, sourceUserName);
            else if (req instanceof Requests.MuteUserInGroupRequest)
                finallyMuteTask(group, (Requests.MuteUserInGroupRequest) req, targetUserName, sourceUserName);
            else if (req instanceof Requests.UnmuteUserInGroupRequest)
                finallyUnmuteTask(group, (Requests.UnmuteUserInGroupRequest)req, targetUserName, sourceUserName);
            else if (req instanceof Requests.CoadminAddRequest)
                finallyAddCoadminTask(group, (Requests.CoadminAddRequest)req, targetUserName, sourceUserName);
            else if (req instanceof Requests.CoadminRemoveRequest)
                finallyRemoveCoadminTask(group, (Requests.CoadminRemoveRequest)req, targetUserName, sourceUserName);
            else
                return;
        }
    }

    private void finallyAddCoadminTask(Group group, Requests.CoadminAddRequest req, String userToAdd, String userName){
        if (!group.getUsers().contains(userToAdd)) {
            sender().tell(String.format("%s is not in %s!", userToAdd, req.groupName), self());
        } else if (!group.getCoAdmins().contains(userToAdd)) {
            group.getCoAdmins().add(userToAdd);
            group.getMutedUsers().remove(userToAdd);
            usersMap.get(userToAdd).tell(new Notifications.CoadminAddNotification(req.groupName), usersMap.get(userName));
            sender().tell("", self());
        } else {
            sender().tell("", self());
        }
    }

    private void finallyRemoveCoadminTask(Group group, Requests.CoadminRemoveRequest req, String userToRemove, String userName){
        if (!group.getUsers().contains(userToRemove)) {
            sender().tell(String.format("%s is not in %s!", userToRemove, req.groupName), self());
        } else if (group.getCoAdmins().contains(userToRemove)) {
            group.getCoAdmins().remove(userToRemove);
            usersMap.get(userToRemove).tell(new Notifications.CoadminRemoveNotification(req.groupName), usersMap.get(userName));
            sender().tell("", self());
        } else {
            sender().tell("", self());
        }
    }

    private void finallyUnmuteTask(Group group, Requests.UnmuteUserInGroupRequest req, String userToUnmute, String userName){
        if (!group.getUsers().contains(userToUnmute)) {
            sender().tell(String.format("%s is not in %s!", userToUnmute, req.groupName), self());
        } else {
            if (!group.getMutedUsers().containsKey(userToUnmute)) {
                sender().tell(String.format("%s is not muted!", userToUnmute), self());
            } else {
                group.getMutedUsers().get(userToUnmute).cancelable.cancel();
                group.getMutedUsers().remove(userToUnmute);
                usersMap.get(userToUnmute).tell(new Notifications.UnmuteNotification(req.groupName, userName), usersMap.get(userName));
                sender().tell("", self());
            }
        }
    }

    private void finallyMuteTask(Group group, Requests.MuteUserInGroupRequest req, String userToMute, String userName){
        if (!group.getUsers().contains(userToMute)) {
            sender().tell(String.format("%s is not in %s!", userToMute, req.groupName), self());
        } else {
            group.getCoAdmins().remove(userToMute);
            // Setting up scheduler task
            ActorRef self = self();
            Cancellable cancellable = getContext().system().scheduler().
                    scheduleOnce(Duration.ofSeconds(req.timeSec), () -> {
                        usersMap.get(userToMute).tell(new Notifications.MuteTimedUpNotification(req.groupName), self);
                        group.getMutedUsers().remove(userToMute);
                    }, getContext().dispatcher());
            group.getMutedUsers().put(userToMute, new MutedUser(userToMute, System.currentTimeMillis(), req.timeSec, cancellable));
            usersMap.get(userToMute).tell(new Notifications.MuteNotification(req.groupName, req.timeSec, userName), usersMap.get(userName));
            sender().tell("", self());
        }
    }

    private void finallyInviteTask(Requests.InviteToGroupRequest req, String userToInvite, String userName){
        usersMap.get(userToInvite).tell(req, usersMap.get(userName));
        sender().tell("", self());
    }

    private void finallyRemoveTask(Group group, Requests.RemoveFromGroupRequest req, String userToDelete, String userName){
        if (!group.getUsers().contains(userToDelete)) {
            sender().tell(String.format("%s is not in %s!", userToDelete, req.groupName), self());
        } else {
            group.getCoAdmins().remove(userToDelete);
            group.getMutedUsers().remove(userToDelete);
            group.getUsers().remove(userToDelete);
            group.router = group.getRouter().removeRoutee(usersMap.get(userToDelete));
            usersMap.get(userToDelete).tell(new Notifications.RemovedNotification(req.groupName, userToDelete, userName), usersMap.get(userName));
            sender().tell("", self());
        }
    }

    private void groupMessageHandler(Message.AbstractGroupMsg msg){
        String userName = msg.sourceUserName ;
        String groupName = msg.targetName;
        Group targetGroup = groupMap.get(groupName);
        if (targetGroup == null) {
            // the group does not exists
            sender().tell(String.format("%s does not exist!", groupName), self());
        } else if (!targetGroup.getUsers().contains(userName)) {
            // the user is not part of the group
            sender().tell(String.format("You are not part of %s!", groupName), self());
        } else {
            MutedUser muted = targetGroup.getMutedUsers().get(userName);
            if (muted != null) {
                // the user is muted
                String m = String.format("You are muted for %d in %s!", System.currentTimeMillis() - (muted.startTime + muted.interval), groupName);
                sender().tell(m, self());
            } else {
                // can broadcast message
                Router groupRouter = targetGroup.getRouter();
                ActorRef userActor = usersMap.get(userName);
                groupRouter.route(new Broadcast(msg), userActor);
                // symbol v like whatsapp
                sender().tell("V", self());
            }
        }
    }

    private void leaveGroupRequestHandler(Requests.LeaveGroupRequest req) {
        String userName = req.userName;
        String groupName = req.groupName;
        Group group = groupMap.get(req.groupName);
        if (group == null) {
            // if the group does not exists
            sender().tell(groupName + " does not exist!", self());
        } else if (!group.getUsers().contains(userName)) {
            // if the user does not exists
            sender().tell(String.format("%s is not in %s!", userName, groupName), self());
        } else {
            // User should be removed
            Router groupRouter = group.getRouter();
            group.getUsers().remove(userName);
            groupRouter.removeRoutee(usersMap.get(userName));
            group.getMutedUsers().remove(userName);
            group.getCoAdmins().remove(userName);
            // broadcasting user leaving
            Notifications.GroupBroadcast groupUpdate = new Notifications.GroupBroadcast(String.format("%s has left %s", userName, group));
            groupRouter.route(new Broadcast(groupUpdate), self());
            if (group.getAdmin().equals(userName)) {
                // the use id the admin of the group
                Notifications.GroupBroadcast groupDelete = new Notifications.GroupBroadcast(String.format("%s admin has closed %s!", userName, group));
                groupRouter.route(new Broadcast(groupDelete), self());
                groupMap.remove(groupName);
                sender().tell(String.format("you deleted the group %s", groupName), self());
            } else {
                sender().tell(String.format("you left the group %s", groupName), self());
            }
        }
    }

    private void createGroupRequestHandler(Requests.CreateGroupRequest req) {
        String admin = req.adminName;
        String groupName = req.groupName;
        // if group exists or user is not connected
        if(groupMap.get(groupName) != null || usersMap.get(admin) == null){
            sender().tell(Boolean.FALSE, self());
        }
        else{
            ActorRef adminActor = usersMap.get(admin);
            groupMap.put(groupName, new Group(groupName, admin, adminActor));
            sender().tell(Boolean.TRUE, self());
        }
    }

    private void getActorRequestHandler(Requests.GetActorRequest getActorRequest) {
        sender().tell(usersMap.get(getActorRequest.username), self());
    }

    private void connectRequestHandler(Requests.ConnectRequest connectRequest){
        String username = connectRequest.username;
        if (usersMap.containsKey(username)){
            sender().tell(new Replys.ConnectReply(false), self());
        }
        else{
            usersMap.put(username,sender());
            sender().tell(new Replys.ConnectReply(true), self());
        }
    }

    private void disconnectHandler(Requests.DisconnectRequest disconnectRequest){
        String username = disconnectRequest.username;
        usersMap.remove(username);
        sender().tell(new Replys.DisconnectReply(true), self());
    }

    public static Props props() {
        return Props.create(Server.class);
    }

    public static void main(String[] args) {
        Config conf = ConfigFactory.load();
        ActorSystem system = ActorSystem.create("Server", conf);
        system.actorOf(Server.props(), "server");
    }
}
