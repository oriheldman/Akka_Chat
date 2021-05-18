import akka.actor.*;
import akka.util.Timeout;
import com.typesafe.config.ConfigFactory;
import scala.concurrent.Await;
import scala.concurrent.Future;

import java.io.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Scanner;

import static akka.pattern.Patterns.ask;

public class Client extends AbstractActor{

    private String username;
    private String ip;
    private ActorRef serverActor;

    public Client(String ip) {
        this.ip = ip;
    }

    private Receive disconnectedBehaviour = receiveBuilder()
            .match(Requests.DisconnectRequest.class, this::disconnectRequestCommand)
            .match(Requests.ConnectRequest.class, this::connectRequestCommand)
            .match(ActorIdentity.class, this::identityHandler)
            .match(Replys.ConnectReply.class, this::connectReplyHandler)
            .build();

    private Receive connectedBehaviour = receiveBuilder()
            // outgoing messages | Requests
            .match(Requests.DisconnectRequest.class, this::disconnectRequestCommand)
            .match(Message.AbstractUserMsg.class, msg -> msg.sourceUserName == null, this::userSendMsgCommand)
            .match(Message.AbstractGroupMsg.class, msg -> msg.sourceUserName == null, this::userSendRequestCommand)
//            .match(Requests.TextRequest.class, this::textRequestCommand)
            .match(Requests.CreateGroupRequest.class, this::createGroupCommand)
            .match(Requests.LeaveGroupRequest.class, this::leaveGroupCommand)
            .match(Requests.InviteToGroupRequest.class, msg -> msg.sourceUserName == null, this::userSendRequestCommand)
            .match(Requests.InviteToGroupRequest.class, this::receiveGroupInvitationHandler)
            .match(Requests.RemoveFromGroupRequest.class, msg -> msg.sourceUserName== null, this::userSendRequestCommand)
            .match(Requests.GroupAdminRequest.class, msg -> msg.sourceUserName == null, this::userSendRequestCommand)
            .match(Requests.MuteUserInGroupRequest.class, msg -> msg.sourceUserName == null, this::userSendRequestCommand)
            .match(Requests.UnmuteUserInGroupRequest.class, msg -> msg.sourceUserName == null, this::userSendRequestCommand)

            // incoming messages | Replys | Notifications
            .match(Replys.DisconnectReply.class, this:: disconnectReplyHandler)
            .match(Message.TextMsgUser.class, this::userReceiveMsgHandler)
            .match(Message.FileMsgUser.class, this::userReceiveFileHandler)
            .match(Message.GroupTextMessage.class, this::receiveGroupTextMessageHandler)
            .match(Message.GroupFileMessage.class, this::receiveGroupFileMessageHandler)

            .match(Notifications.GroupBroadcast.class, this::receiveGroupBroadcastHandler)
            .match(Notifications.MuteNotification.class, this::groupMuteNotifyHandler)
            .match(Notifications.UnmuteNotification.class, this::groupUnmuteNotifyHandler)
            .match(Notifications.MuteTimedUpNotification.class, this::groupMuteTimedUpNotifyHandler)
            .match(Notifications.InvitationNotification.class, this::receiveGroupInvitationReplyHandler)
            .match(Notifications.RemovedNotification.class, this::removeNotifyHandler)
            .match(Notifications.CoadminAddNotification.class, this::coadminAddNotifyHandler)
            .match(Notifications.CoadminRemoveNotification.class, this::coadminRemoveNotifyHandler)

            .build();


    static Object askServer(ActorRef server, Object message) {
        Timeout t = Timeout.create(Duration.ofSeconds(20));
        Future<Object> f = ask(server, message, t);
        try {
            return Await.result(f, t.duration());
        } catch (Exception ignored) {
        }
        return null;
    }

    private void groupMuteTimedUpNotifyHandler(Notifications.MuteTimedUpNotification p) {
        System.out.println(String.format("You have been unmuted from %s! Muting time is up!", p.groupName));
    }

    private void groupUnmuteNotifyHandler(Notifications.UnmuteNotification p) {
        System.out.println(String.format("You have been unmuted in %s by %s!", p.groupName, p.sourceUserName));
    }

    private void groupMuteNotifyHandler(Notifications.MuteNotification p) {
        System.out.println(String.format("You have been muted for %d seconds in %s by %s!", p.timeSec, p.groupName, p.sourceUserName));
    }

    private void receiveGroupBroadcastHandler(Notifications.GroupBroadcast p) {
        System.out.println(p.message);
    }

    private void coadminRemoveNotifyHandler(Notifications.CoadminRemoveNotification p) {
        System.out.println(String.format("You have been demoted to user in %s!", p.groupName));
    }

    private void coadminAddNotifyHandler(Notifications.CoadminAddNotification p) {
        System.out.println(String.format("You have been promoted to co-admin in %s!", p.groupName));
    }

    private void removeNotifyHandler(Notifications.RemovedNotification req) {
        System.out.println(String.format("[%s][%s][%s]: You have been removed from %s by %s!",
                DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now()), req.groupName, req.sourceUserName,
                req.groupName, req.sourceUserName));
    }

    private void receiveGroupInvitationReplyHandler(Notifications.InvitationNotification res) {
        if (res.approved) {
            String groupName = res.groupName;
            serverActor.tell(new Requests.AddUserToGroup(groupName, res.targetUser), self());
            String message = String.format("Welcome to %s!", groupName);
            sender().tell(new Message.TextMsgUser(this.username, res.targetUser, message), self());
        } else {
            System.out.println(String.format("%s declined the invitation", res.targetUser));
        }
    }

    private void receiveGroupInvitationHandler(Requests.InviteToGroupRequest req) {
        String groupName = req.groupName;
        ActorRef sender = sender();
        System.out.println(String.format("You have been invited to %s, Accept(Yes/No)?", groupName));
        Receive waitForInviteResponse = receiveBuilder()
                .matchEquals("Yes", s -> {
                    sender.tell(new Notifications.InvitationNotification(true, req.sourceUserName, req.groupName,
                            req.targetUserName), self());
                    getContext().become(this.connectedBehaviour);
                })
                .matchEquals("No", s -> {
                    sender.tell(new Notifications.InvitationNotification(false, req.sourceUserName, req.groupName,
                            req.targetUserName), self());
                    getContext().become(this.connectedBehaviour);
                })
                .build();
        getContext().become(waitForInviteResponse);
    }

    // group messages where targetName is the group name
    private void receiveGroupFileMessageHandler(Message.GroupFileMessage m) {
        File inputFile = new File(String.format("whatsapp_downloads/%s/%s/%s", m.targetName, m.sourceUserName, m.content.getName()));
        inputFile.getParentFile().mkdirs();
        try {
            OutputStream outputStream = new FileOutputStream(inputFile);
            InputStream inputStream = new FileInputStream(m.content);
            int read;
            int offset = 0;
            byte[] buffer = new byte[1024];
            while ((read = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, offset, read);
                offset += read;
            }
        } catch (Exception ignored) {
        }
        System.out.println(String.format("[%s][%s][%s] File received: %s",
                DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now()),
                m.targetName,
                m.sourceUserName,
                inputFile.getAbsolutePath()));
    }

    private void receiveGroupTextMessageHandler(Message.GroupTextMessage m) {
        System.out.println(String.format("[%s][%s][%s] %s",
                DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now()),
                m.targetName,
                m.sourceUserName,
                m.content));
    }

    private void userSendRequestCommand(Requests.AbstractRequest req) {
        req.sourceUserName = username;
        String response = (String) askServer(serverActor, req);
        if (response != null && !response.equals("")) {
            System.out.println(response);
        }
    }

    private void userSendMsgCommand(Message.AbstractUserMsg msg) {
        ActorRef userTarget = (ActorRef) askServer(serverActor, new Requests.GetActorRequest(msg.targetUserName));
        if (userTarget == null) {
            System.out.println(String.format("%s does not exist!", msg.targetUserName));
        } else {
            // edit before sending
            msg.sourceUserName = username;
            userTarget.tell(msg, self());
        }
    }

    private void leaveGroupCommand(Requests.LeaveGroupRequest leaveGroupRequest) {
        leaveGroupRequest.userName = this.username;
        String response = (String) askServer(serverActor, leaveGroupRequest);
        if (response != null && !response.equals("")) {
            System.out.println(response);
        }
    }

    private void createGroupCommand(Requests.CreateGroupRequest createGroupRequest) {
        createGroupRequest.adminName = this.username;
        Boolean response = (Boolean) askServer(serverActor, createGroupRequest);
        String message;
        if (serverActor.isTerminated() || response == null){
            message = "server is offline!";
        }
        else if (response){
            message = "created successfully!";
        }
        else{
            message = "already exists!";
        }
        System.out.println(String.format("%s %s", createGroupRequest.groupName, message));
    }

    private void textRequestCommand(Requests.TextRequest req) {
        if(serverActor.isTerminated()){
            System.out.println("server is offline!");
        }
        else{
            serverActor.tell(req, self());
        }
    }

    private void disconnectRequestCommand(Requests.DisconnectRequest req) {
//        Boolean res = (Boolean) askServer(serverActor, req);
        if(serverActor.isTerminated()){
            System.out.println("server is offline! try again later!");
        }
        else{
            req.username = this.username;
            serverActor.tell(req, self());
        }
    }

    private void connectRequestCommand(Requests.ConnectRequest request) {
        username = request.username;
        try {
            getContext().actorSelection("akka.tcp://Server@" + ip + ":1994/user/server")
                    .tell(new Identify(username), self());
        } catch (ActorNotFound e) {
            System.out.println("server is offline!");
        }
    }

    private void identityHandler(ActorIdentity identity) {
        Optional<ActorRef> actorRef = identity.getActorRef();
        if (actorRef.isPresent()) {
            serverActor = actorRef.get();
            // getContext().watch(server);
            serverActor.tell(new Requests.ConnectRequest(username), self());
        } else {
            System.out.println("server is offline!");
        }
    }

    private void connectReplyHandler(Replys.ConnectReply connectReply) {
        if (connectReply.isConnected){
            System.out.println(String.format("%s has been connected successfully!", this.username));
            getContext().become(this.connectedBehaviour);
        }
        else{
            System.out.println(String.format("%s is in use!", this.username));
        }
    }

    private void disconnectReplyHandler(Replys.DisconnectReply disconnectReply){
        if (disconnectReply.isDisconnected){
            System.out.println(String.format("%s has been disconnected successfully!", this.username));
            getContext().become(this.disconnectedBehaviour);
        }
        else{
            System.out.println(String.format("%s could not disconnect!", this.username));
        }
    }

    private void userReceiveFileHandler(Message.FileMsgUser fileMsgUser) {
        File inputFile = new File(String.format("whatsapp_downloads/%s/%s", fileMsgUser.sourceUserName, fileMsgUser.content.getName()));
        inputFile.getParentFile().mkdirs();
        try {
            OutputStream outputStream = new FileOutputStream(inputFile);
            InputStream inputStream = new FileInputStream(fileMsgUser.content);
            int read;
            int offset = 0;
            byte[] buffer = new byte[1024];
            while ((read = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, offset, read);
                offset += read;
            }
        } catch (Exception ignored) {
        }
        System.out.println(String.format("[%s][%s][%s] File received: %s",
                DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now()),
                fileMsgUser.targetUserName,
                fileMsgUser.sourceUserName,
                inputFile.getAbsolutePath()));
    }

    private void userReceiveMsgHandler(Message.TextMsgUser msg) {
        System.out.println(String.format("[%s][%s][%s] %s",
                DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now()),
                msg.targetUserName,
                msg.sourceUserName,
                msg.content));
    }

    @Override
    public Receive createReceive() {
        return this.disconnectedBehaviour;
    }

    public static void main(String[] args) {
        String id = String.valueOf((int) (Math.random() * 100));
        ActorSystem system =
                ActorSystem.create("User", ConfigFactory.parseString(String.format("akka {\n" +
                        "  loglevel = off\n" +
                        "  actor {\n" +
                        "    provider = \"akka.remote.RemoteActorRefProvider\"\n" +
                        "    warn-about-java-serializer-usage = false\n" +
                        "  }\n" +
                        "  remote {\n" +
                        "    enabled-transports = [\"akka.remote.netty.tcp\"]\n" +
                        "    netty.tcp {\n" +
                        "      hostname = \"%s\"\n" +
                        "      port = %s\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", args[0], args[1])));
        ActorRef user = system.actorOf(Props.create(Client.class, args[0]), id);
        Scanner scanner = new Scanner(System.in);
        String input;
        Boolean active = true;
        while (active) {
            input = scanner.nextLine();
            if ("quit".equals(input)) {
                active = false;
            }
            else {
                try {
                    user.tell(Usable.parseCommand(input), ActorRef.noSender());
                } catch (FileNotFoundException e) {
                    System.out.println(String.format("%s does not exist!", e.getMessage()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        system.terminate();
    }
}
