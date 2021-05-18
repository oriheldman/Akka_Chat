import akka.actor.ActorRef;
import akka.routing.BroadcastRoutingLogic;
import akka.routing.Router;

import java.util.ArrayList;
import java.util.HashMap;

public class Group {

    private String groupName;
    private String admin;
    private ArrayList<String> coAdmins = new ArrayList<String>();
    private ArrayList<String> users = new ArrayList<String>();
    private HashMap<String, MutedUser> mutedUsers = new HashMap<>();
    public Router router;

    public Group(String groupName, String adminUserName, ActorRef adminActorRef){
        this.groupName = groupName;
        this.admin = adminUserName;
        users.add(adminUserName);
        this.router = new Router(new BroadcastRoutingLogic()).addRoutee(adminActorRef);
    }

    public String getAdmin() {
        return admin;
    }

    public ArrayList<String> getCoAdmins() {
        return coAdmins;
    }

    public ArrayList<String> getUsers() {
        return users;
    }

    public HashMap<String, MutedUser> getMutedUsers() {
        return mutedUsers;
    }

    public Router getRouter() {
        return router;
    }
}
