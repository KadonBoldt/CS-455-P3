package client;

import server.Server;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Scanner;
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

    public static HashMap<String, String> arguments = new HashMap<>();
    public static LinkedList<String> serverList = new LinkedList<>();
    public static int port = Server.DEFAULT_PORT;
    public static String coordinator = null;

    /**
     * Main driver of the client-side program.
     * @param args - command line arguments.
     */
    public static void main(String[] args) {
        System.setProperty("javax.net.ssl.trustStore", "resources/Client_Truststore");
        System.setProperty("java.security.policy", "resources/mysecurity.policy");
        System.setProperty("javax.net.ssl.trustStorePassword", Server.SSL_PASSWORD);

        parseArgs(args);
        setCoordinator();

        if (coordinator == null) {
            System.out.println("[IdClient] Error: Couldn't identify the coordinator.");
            System.exit(1);
        }

        System.out.println("[IdClient/" + coordinator + "] " + execute());
    }

    /**
     * Goes through server list and acquires coordinator address. Starts an election
     * if current coordinator doesn't exist.
     */
    public static void setCoordinator() {
        for (String host : serverList) {
            try {
                IdClient client = new IdClient(host, port);
                String getCoordinator = client.getCoordinator();
                if (getCoordinator == null) {
                    coordinator = host;
                    break;
                }
                else {
                    try {
                        IdClient coordinatorClient = new IdClient(getCoordinator, port);
                        String testCoordinator = coordinatorClient.getCoordinator();
                        if (testCoordinator == null) {
                            coordinator = getCoordinator;
                            break;
                        }
                        else {
                            throw new RemoteException();
                        }
                    }
                    catch (RemoteException|NotBoundException e) {
                        coordinator = client.election();
                    }
                }
            }
            catch (RemoteException|NotBoundException e) {
                continue;
            }
        }
    }

    /**
     * Parses the provided server file to obtain server list.
     * @param fileName - server list file.
     */
    private static void parseServerList(String fileName) {
        try {
            Scanner scanner = new Scanner(new File(fileName));
            while (scanner.hasNextLine()) {
                serverList.add(scanner.nextLine());
            }
            scanner.close();
            if (serverList.isEmpty()) {
                System.err.println("[IdClient] Error: File " + fileName + " is empty.");
                System.exit(1);
            }
        }
        catch (FileNotFoundException e) {
            System.err.println("[IdClient] Error: File " + fileName + " not found.");
            System.exit(1);
        }
    }

    /**
     * Parses and maps the command line arguments.
     * @param args - command line arguments.
     */
    private static void parseArgs(String[] args) {
        if (args.length == 0) {
            showUsage(0);
        }
        try {
            if (!(args[0].equals("--server") || args[0].equals("-s"))) {
                showUsage(1);
            }
            int queryArg = 2;
            if (args[queryArg].equals("--numport") || args[queryArg].equals("-n")) {
                port = Integer.parseInt(args[queryArg + 1]);
                queryArg += 2;
            }
            switch (args[queryArg++]) {
                case "--create":
                case "-c":
                    arguments.put("queryType", "create");
                    arguments.put("loginName", args[queryArg]);
                    switch (args.length - queryArg) {
                        case 1:
                            arguments.put("realName", System.getProperty("user.name"));
                            break;
                        case 2:
                            arguments.put("realName", args[queryArg + 1]);
                            break;
                        case 3:
                            if (!(args[queryArg + 1].equals("--password") || args[queryArg + 1].equals("-p"))) {
                                throw new IndexOutOfBoundsException();
                            }
                            arguments.put("realName", System.getProperty("user.name"));
                            arguments.put("password", args[queryArg + 2]);
                            break;
                        case 4:
                            if (!(args[queryArg + 2].equals("--password") || args[queryArg + 2].equals("-p"))) {
                                throw new IndexOutOfBoundsException();
                            }
                            arguments.put("realName", args[queryArg + 1]);
                            arguments.put("password", args[queryArg + 3]);
                            break;
                        default:
                            throw new IndexOutOfBoundsException();
                    }
                    break;
                case "--lookup":
                case "-l":
                    if (args.length > queryArg + 1) {
                        throw new IndexOutOfBoundsException();
                    }
                    arguments.put("queryType", "lookup");
                    arguments.put("loginName", args[queryArg]);
                    break;
                case "--reverse-lookup":
                case "-r":
                    if (args.length > queryArg + 1) {
                        throw new IndexOutOfBoundsException();
                    }
                    arguments.put("queryType", "reverseLookup");
                    arguments.put("UUID", args[queryArg]);
                    break;
                case "--modify":
                case "-m":
                    arguments.put("queryType", "modify");
                    arguments.put("loginName", args[queryArg]);
                    arguments.put("newLoginName", args[queryArg + 1]);
                    switch (args.length - queryArg) {
                        case 2:
                            break;
                        case 4:
                            if (!(args[queryArg + 2].equals("--password") || args[queryArg + 2].equals("-p"))) {
                                throw new IndexOutOfBoundsException();
                            }
                            arguments.put("password", args[queryArg + 3]);
                            break;
                        default:
                            throw new IndexOutOfBoundsException();
                    }
                    break;
                case "--delete":
                case "-d":
                    arguments.put("queryType", "delete");
                    arguments.put("loginName", args[queryArg]);
                    switch (args.length - queryArg) {
                        case 1:
                            break;
                        case 3:
                            if (!(args[queryArg + 1].equals("--password") || args[queryArg + 1].equals("-p"))) {
                                throw new IndexOutOfBoundsException();
                            }
                            arguments.put("password", args[queryArg + 2]);
                        default:
                            throw new IndexOutOfBoundsException();
                    }
                    break;
                case "--get":
                case "-g":
                    if (args.length > queryArg + 1 || !(args[queryArg].equals("users") || args[queryArg].equals("uuids") || args[queryArg].equals("all"))) {
                        throw new IndexOutOfBoundsException();
                    }
                    arguments.put("queryType", "get");
                    arguments.put("getType", args[queryArg]);
                    break;
                default:
                    throw new IndexOutOfBoundsException();
            }
            parseServerList(args[1]);
        }
        catch (IndexOutOfBoundsException|NumberFormatException e) {
            System.err.println("[IdClient] Error: Invalid format, please use the provided usage!");
            showUsage(1);
        }
    }

    /**
     * Prints usage statement then exits the program.
     * @param code - exit code.
     */
    private static void showUsage(int code) {
        System.out.println(
                """
                        Usage:      IdClient --server/-s <filename> [--numport/-n <port#>] <query>
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
    private static String encryptPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            return new String(digest.digest(password.getBytes()));
        }
        catch (NoSuchAlgorithmException e) {
            System.err.println("[IdClient] Error: " + e.getMessage());
            System.exit(1);
        }
        return null;
    }

    /**
     * Uses command line arguments to send a message to the coordinator.
     * @return - coordinator response message or appropriate error message.
     */
    private static String execute() {
        try {
            IdClient client = new IdClient(coordinator, port);
            switch (arguments.get("queryType")) {
                case "create":
                    if (arguments.get("password") == null) {
                        return client.create(arguments.get("loginName"), arguments.get("realName"));
                    }
                    return client.create(arguments.get("loginName"), arguments.get("realName"), arguments.get("password"));
                case "lookup":
                    return client.lookup(arguments.get("loginName"));
                case "reverseLookup":
                    return client.reverseLookup(UUID.fromString(arguments.get("UUID")));
                case "modify":
                    if (arguments.get("password") == null) {
                        return client.modify(arguments.get("loginName"), arguments.get("newLoginName"));
                    }
                    return client.modify(arguments.get("loginName"), arguments.get("newLoginName"), arguments.get("password"));
                case "delete":
                    if (arguments.get("password") == null) {
                        return client.delete(arguments.get("loginName"));
                    }
                    return client.delete(arguments.get("loginName"), arguments.get("password"));
                case "get":
                    switch (arguments.get("getType")) {
                        case "users":
                            return client.get(Server.getType.USERS);
                        case "uuids":
                            return client.get(Server.getType.UUIDS);
                        case "all":
                            return client.get(Server.getType.ALL);
                    }
                    break;
            }
        }
        catch (RemoteException|NotBoundException e) {
            return "Error: Couldn't connect to the remote host.";
        }
        return "Hmm... something went wrong.";
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
        return server.create(loginName, realName, null);
    }

    /**
     * Creates an account for the provided credentials and adds it to the database.
     * @param loginName - unique login name.
     * @param realName - real name of the user.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String create(String loginName, String realName, String password) throws RemoteException {
        return server.create(loginName, realName, encryptPassword(password), null);
    }

    /**
     * Looks up an account in the database using login name and retrieves its information.
     * @param loginName - unique login name.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String lookup(String loginName) throws RemoteException {
        return server.lookup(loginName, null);
    }

    /**
     * Looks up an account in the database using UUID and retrieves its information.
     * @param id - UUID of user.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String reverseLookup(UUID id) throws RemoteException {
        return server.reverseLookup(id, null);
    }

    /**
     * Modifies an account's login name to the new provided name.
     * @param oldLoginName - original login name.
     * @param newLoginName - login name to change to.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String modify(String oldLoginName, String newLoginName) throws RemoteException {
        return server.modify(oldLoginName, newLoginName, null);
    }

    /**
     * Modifies an account's login name to the new provided name.
     * @param oldLoginName - original login name.
     * @param newLoginName - login name to change to.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String modify(String oldLoginName, String newLoginName, String password) throws RemoteException {
        return server.modify(oldLoginName, newLoginName, encryptPassword(password), null);
    }

    /**
     * Deletes the identified account from the database.
     * @param loginName - unique login name.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String delete(String loginName) throws RemoteException {
        return server.delete(loginName, null);
    }

    /**
     * Deletes the identified account from the database.
     * @param loginName - unique login name.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String delete(String loginName, String password) throws RemoteException {
        return server.delete(loginName, encryptPassword(password), null);
    }

    /**
     * Retrieves data on the accounts stored within the database.
     * @param type - type of data to retrieve.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String get(Server.getType type) throws RemoteException {
        return server.get(type, null);
    }

    /**
     * Returns whether the server is the coordinator or not.
     * @return - "yes" if server is coordinator, or the IP address of coordinator if not.
     * @throws RemoteException - in case of remote error.
     */
    public String getCoordinator() throws RemoteException {
        return server.getCoordinator();
    }

    /**
     * Starts an election for a coordinator.
     * @return - host name of new coordinator.
     * @throws RemoteException - in case or remote error.
     */
    public String election() throws RemoteException {
        return server.election();
    }

}