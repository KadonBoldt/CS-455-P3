package server;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import java.util.UUID;

public class IdServer extends UnicastRemoteObject implements Server {

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        boolean verbose = false;
        try {
            switch (args.length) {
                case 0:
                    showUsage(0);
                    break;
                case 1:
                    if (!(args[0].equals("--verbose") || args[0].equals("-v"))) {
                        throw new NumberFormatException();
                    }
                    verbose = true;
                    break;
                case 2:
                    if (!(args[0].equals("--numport") || args[0].equals("-n"))) {
                        throw new NumberFormatException();
                    }
                    port = Integer.parseInt(args[1]);
                    break;
                case 3:
                    if (!(args[0].equals("--numport") || args[0].equals("-n")) || !(args[2].equals("--verbose") || args[2].equals("-v"))) {
                        throw new NumberFormatException();
                    }
                    port = Integer.parseInt(args[1]);
                    verbose = true;
                    break;
                default:
                    throw new NumberFormatException();
            }
            IdServer server = new IdServer(port, verbose);
            server.bind();
            System.out.println("[IdServer] Server started on port " + port + "!");
        }
        catch (NumberFormatException e) {
            System.err.println("Error: Invalid format, please use the provided usage!");
            showUsage(1);
        }
        catch (RemoteException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public static void showUsage(int code) {
        System.out.println("Usage: IdServer [--numport/-n <port#>] [--verbose/-v]");
        System.exit(code);
    }

    public int port;
    public boolean verbose;

    public IdServer(int port, boolean verbose) throws RemoteException {
        super();
        this.port = port;
        this.verbose = verbose;
    }

    public void bind() {
        try {
            RMIClientSocketFactory rmiClientSocketFactory = new SslRMIClientSocketFactory();
            RMIServerSocketFactory rmiServerSocketFactory = new SslRMIServerSocketFactory();
            Server ccAuth = (Server) UnicastRemoteObject.exportObject(this, 0, rmiClientSocketFactory, rmiServerSocketFactory);
            Registry registry = LocateRegistry.createRegistry(port);
            registry.rebind(SERVER_NAME, ccAuth);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public synchronized String create(String loginName, String realName) throws RemoteException {
        return "";
    }

    public synchronized String create(String loginName, String realName, String password) throws RemoteException {
        return "";
    }

    public synchronized String lookup(String loginName) throws RemoteException {
        return "";
    }

    public synchronized String reverseLookup(UUID id) throws RemoteException {
        return "";
    }

    public synchronized String modify(String oldLoginName, String newLoginName) throws RemoteException {
        return "";
    }

    public synchronized String modify(String oldLoginName, String newLoginName, String password) throws RemoteException {
        return "";
    }

    public synchronized String delete(String loginName) throws RemoteException {
        return "";
    }

    public synchronized String delete(String loginName, String password) throws RemoteException {
        return "";
    }

    public synchronized String get(getType type) throws RemoteException {
        return "";
    }
}
