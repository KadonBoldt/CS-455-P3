package server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.UUID;

public class IdServer extends UnicastRemoteObject implements Server {

    public IdServer() throws RemoteException {}

    public String create(String loginName, String realName) throws RemoteException {
        return "";
    }

    public String create(String loginName, String realName, String password) throws RemoteException {
        return "";
    }

    public String lookup(String loginName) throws RemoteException {
        return "";
    }

    public String reverseLookup(UUID id) throws RemoteException {
        return "";
    }

    public String modify(String oldLoginName, String newLoginName) throws RemoteException {
        return "";
    }

    public String modify(String oldLoginName, String newLoginName, String password) throws RemoteException {
        return "";
    }

    public String delete(String loginName) throws RemoteException {
        return "";
    }

    public String delete(String loginName, String password) throws RemoteException {
        return "";
    }

    public String get(getType type) throws RemoteException {
        return "";
    }
}
