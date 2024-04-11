package client;

import server.Server;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.UUID;

/**
 * This program is the implementation of an ID client. The
 * program allows a user to connect to a server connected
 * to a UUID database, and serves as the UI for managing
 * data via a series of commandline arguments.
 *
 * @author Kadon Boldt
 * @version 1.0
 * @since 4/10/2024
 */
public class IdClient {

    /**
     * Main driver of the client-side program.
     * @param args - command line arguments.
     */
    public static void main(String[] args) {
        System.setProperty("javax.net.ssl.trustStore", "resources/Client_Truststore");
        System.setProperty("java.security.policy", "resources/mysecurity.policy");
        System.setProperty("javax.net.ssl.trustStorePassword", Server.SSL_PASSWORD);
        System.out.println("[IdClient] " + execute(args));
    }

    /**
     * Parses the command line arguments and formulates the desired method call.
     * @param args - command line arguments.
     * @return - server response message.
     */
    public static String execute(String[] args) {
        if (args.length == 0) {
            showUsage(0);
        }
        try {
            if (!(args[0].equals("--server") || args[0].equals("-s"))) {
                throw new IndexOutOfBoundsException();
            }
            int port = Server.DEFAULT_PORT;
            int queryArg = 2;
            if (args[queryArg].equals("--numport") || args[queryArg].equals("-n")) {
                port = Integer.parseInt(args[queryArg + 1]);
                queryArg += 2;
            }
            IdClient client = new IdClient(args[1], port);
            switch (args[queryArg++]) {
                case "--create":
                case "-c":
                    return switch (args.length - queryArg) {
                        case 1 -> client.create(args[queryArg], System.getProperty("user.name"));
                        case 2 -> client.create(args[queryArg], args[queryArg + 1]);
                        case 3 -> {
                            if (!(args[queryArg + 1].equals("--password") || args[queryArg + 1].equals("-p"))) {
                                throw new IndexOutOfBoundsException();
                            }
                            yield client.create(args[queryArg], System.getProperty("user.name"), args[queryArg + 2]);
                        }
                        case 4 -> {
                            if (!(args[queryArg + 2].equals("--password") || args[queryArg + 2].equals("-p"))) {
                                throw new IndexOutOfBoundsException();
                            }
                            yield client.create(args[queryArg], args[queryArg + 1], args[queryArg + 3]);
                        }
                        default -> throw new IndexOutOfBoundsException();
                    };
                case "--lookup":
                case "-l":
                    if (args.length > queryArg + 1) {
                        throw new IndexOutOfBoundsException();
                    }
                    return client.lookup(args[queryArg]);
                case "--reverse-lookup":
                case "-r":
                    if (args.length > queryArg + 1) {
                        throw new IndexOutOfBoundsException();
                    }
                    return client.reverseLookup(UUID.fromString(args[queryArg]));
                case "--modify":
                case "-m":
                    return switch (args.length - queryArg) {
                        case 2 -> client.modify(args[queryArg], args[queryArg + 1]);
                        case 4 -> {
                            if (!(args[queryArg + 2].equals("--password") || args[queryArg + 2].equals("-p"))) {
                                throw new IndexOutOfBoundsException();
                            }
                            yield client.modify(args[queryArg], args[queryArg + 1], args[queryArg + 3]);
                        }
                        default -> throw new IndexOutOfBoundsException();
                    };
                case "--delete":
                case "-d":
                    return switch (args.length - queryArg) {
                        case 1 -> client.delete(args[queryArg]);
                        case 3 -> {
                            if (!(args[queryArg + 1].equals("--password") || args[queryArg + 1].equals("-p"))) {
                                throw new IndexOutOfBoundsException();
                            }
                            yield client.delete(args[queryArg], args[queryArg + 2]);
                        }
                        default -> throw new IndexOutOfBoundsException();
                    };
                case "--get":
                case "-g":
                    if (args.length > queryArg + 1) {
                        throw new IndexOutOfBoundsException();
                    }
                    return switch (args[queryArg]) {
                        case "users" -> client.get(Server.getType.USERS);
                        case "uuids" -> client.get(Server.getType.UUIDS);
                        case "all" -> client.get(Server.getType.ALL);
                        default -> throw new IndexOutOfBoundsException();
                    };
                default:
                    throw new IndexOutOfBoundsException();
            }
        }
        catch (IndexOutOfBoundsException|NumberFormatException e) {
            System.err.println("Error: Invalid format, please use the provided usage!");
            showUsage(1);
        }
        catch (RemoteException|NotBoundException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
            System.exit(1);
        }
        return "Hmm... something went wrong!";
    }

    /**
     * Prints usage statement then exits the program.
     * @param code - exit code.
     */
    public static void showUsage(int code) {
        System.out.println(
                """
                        Usage:      IdClient --server/-s <serverhost> [--numport/-n <port#>] <query>
                        Queries:    --create/-c <loginname> [<real name>] [--password/-p <password>]
                                    --lookup/-l <loginname>
                                    --reverse-lookup/-r <UUID>
                                    --modify/-m <oldloginname> <newloginname> [--password/-p <password>]
                                    --delete/-d <loginname> [--password/-p <password>]
                                    --get/-g users|uuids|all"""
        );
        System.exit(code);
    }

    /**
     * Encrypts a given password using the SHA-512 algorithm.
     * @param password - password to be encrypted.
     * @return - encrypted password.
     */
    public static String encryptPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            return new String(digest.digest(password.getBytes()));
        }
        catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
            System.exit(1);
        }
        return null;
    }

    private Server server = null;

    /**
     * Creates a new IdClient using the given parameters.
     * @param host - hostname to connect to.
     * @param port - port to connect on.
     * @throws RemoteException - in case of remote error.
     * @throws NotBoundException - in case host is not bound to RMI.
     */
    public IdClient(String host, int port) throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(host, port);
        server = (Server) registry.lookup("//" + host + ":" + port + "/" + Server.SERVER_NAME);
    }

    /**
     * Creates an account for the provided credentials and adds it to the database.
     * @param loginName - unique login name.
     * @param realName - real name of the user.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String create(String loginName, String realName) throws RemoteException {
        return server.create(loginName, realName);
    }

    /**
     * Creates an account for the provided credentials and adds it to the database.
     * @param loginName - unique login name.
     * @param realName - real name of the user.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String create(String loginName, String realName, String password) throws RemoteException {
        return server.create(loginName, realName, encryptPassword(password));
    }

    /**
     * Looks up an account in the database using login name and retrieves its information.
     * @param loginName - unique login name.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String lookup(String loginName) throws RemoteException {
        return server.lookup(loginName);
    }

    /**
     * Looks up an account in the database using UUID and retrieves its information.
     * @param id - UUID of user.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String reverseLookup(UUID id) throws RemoteException {
        return server.reverseLookup(id);
    }

    /**
     * Modifies an account's login name to the new provided name.
     * @param oldLoginName - original login name.
     * @param newLoginName - login name to change to.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String modify(String oldLoginName, String newLoginName) throws RemoteException {
        return server.modify(oldLoginName, newLoginName);
    }

    /**
     * Modifies an account's login name to the new provided name.
     * @param oldLoginName - original login name.
     * @param newLoginName - login name to change to.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String modify(String oldLoginName, String newLoginName, String password) throws RemoteException {
        return server.modify(oldLoginName, newLoginName, encryptPassword(password));
    }

    /**
     * Deletes the identified account from the database.
     * @param loginName - unique login name.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String delete(String loginName) throws RemoteException {
        return server.delete(loginName);
    }

    /**
     * Deletes the identified account from the database.
     * @param loginName - unique login name.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String delete(String loginName, String password) throws RemoteException {
        return server.delete(loginName, encryptPassword(password));
    }

    /**
     * Retrieves data on the accounts stored within the database.
     * @param type - type of data to retrieve.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String get(Server.getType type) throws RemoteException {
        return server.get(type);
    }

}
