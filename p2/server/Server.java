package server;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface Server extends Remote {

    public enum getType {
        USERS,
        UUIDS,
        ALL;
    }

    public String create(String loginName, String realName) throws RemoteException;
    public String create(String loginName, String realName, String password) throws RemoteException;

    public String lookup(String loginName) throws RemoteException;

    public String reverseLookup(UUID id) throws RemoteException;

    public String modify(String oldLoginName, String newLoginName) throws RemoteException;
    public String modify(String oldLoginName, String newLoginName, String password) throws RemoteException;

    public String delete(String loginName) throws RemoteException;
    public String delete(String loginName, String password) throws RemoteException;

    public String get(getType type) throws RemoteException;
}
