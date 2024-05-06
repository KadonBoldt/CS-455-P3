package server;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.*;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import java.util.*;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Jedis;

/**
 * This program is the implementation of an ID database RMI server.
 * The program utilizes SSL communication to speak to clients,
 * offering them several options for creating, retrieving, and
 * managing unique UUID based accounts. The program connects to
 * a redis database in order to hold data.
 *
 * @author Kadon Boldt
 * @version 1.0
 * @since 4/10/2024
 */
public class IdServer implements Server {

    private static LinkedList<String> serverList = new LinkedList<>();
    private static int portNum = DEFAULT_PORT;
    private static boolean isVerbose = false;
    private static String IP_HOST = null;

    /**
     * Main driver of the server-side program.
     * @param args - command line arguments.
     */
    public static void main(String[] args) {
        System.setProperty("javax.net.ssl.trustStore", "resources/Client_Truststore");
        System.setProperty("javax.net.ssl.trustStorePassword", SSL_PASSWORD);
        System.setProperty("javax.net.ssl.keyStore", "resources/Server_Keystore");
        System.setProperty("javax.net.ssl.keyStorePassword", SSL_PASSWORD);
        System.setProperty("java.security.policy", "resources/mysecurity.policy");

        parseArgs(args);

        try {
            IdServer server = new IdServer(portNum, isVerbose);
            server.bind();
            System.out.println("[IdServer] Server started on port " + portNum + "!");
            server.bootUp();
        }
        catch (RemoteException e) {
            System.out.println("[IdServer] Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Parses the command line arguments.
     * @param args - command line arguments.
     */
    private static void parseArgs(String[] args) {
        try {
            if (args.length == 0) {
                showUsage(0);
            }
            if (!(args[0].equals("--server") || args[0].equals("-s"))) {
                throw new NumberFormatException();
            }
            switch (args.length) {
                case 3:
                    if (!(args[2].equals("--verbose") || args[2].equals("-v"))) {
                        throw new NumberFormatException();
                    }
                    isVerbose = true;
                    break;
                case 4:
                    if (!(args[2].equals("--numport") || args[2].equals("-n"))) {
                        throw new NumberFormatException();
                    }
                    portNum = Integer.parseInt(args[3]);
                    break;
                case 5:
                    if (!(args[2].equals("--numport") || args[2].equals("-n")) || !(args[4].equals("--verbose") || args[4].equals("-v"))) {
                        throw new NumberFormatException();
                    }
                    portNum = Integer.parseInt(args[3]);
                    isVerbose = true;
                    break;
                default:
                    throw new NumberFormatException();
            }
            IP_HOST = InetAddress.getLocalHost().getHostName();
            parseServerList(args[1]);
        }
        catch (NumberFormatException e) {
            System.err.println("[IdServer] Error: Invalid format, please use the provided usage!");
            showUsage(1);
        }
        catch (UnknownHostException e) {
            System.err.println("[IdServer] Error: You must be connected to a network to use this program!");
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
                System.err.println("[IdServer] Error: File " + fileName + " is empty.");
                System.exit(1);
            }
        }
        catch (FileNotFoundException e) {
            System.err.println("[IdServer] Error: File " + fileName + " not found.");
            System.exit(1);
        }
    }

    /**
     * Prints usage statement then exits the program.
     * @param code - exit code.
     */
    public static void showUsage(int code) {
        System.out.println("Usage: IdServer --server/-s <filename> [--numport/-n <port#>] [--verbose/-v]");
        System.exit(code);
    }

    private int port;
    private boolean verbose;
    private JedisPool pool;
    private Map<String, String> loginToUUID = new HashMap<>();
    private IdCoordinator coordinator = null;
    private String coordinatorHost = null;

    /**
     * Creates a new IdServer with the given parameters.
     * @param port - port to run RMI on.
     * @param verbose - whether detailed information will be logged.
     * @throws RemoteException - in case of remote error.
     */
    public IdServer(int port, boolean verbose) throws RemoteException {
        super();
        this.port = port;
        this.verbose = verbose;
        pool = new JedisPool("localhost", JEDIS_PORT);

        // Instantiate login to UUID map.
        try (Jedis jedis = pool.getResource()) {
            Set<String> keys = jedis.keys("*");
            for (String key : keys) {
                loginToUUID.put(jedis.hgetAll(key).get("login_name"), key);
            }
        }

        // Set shutdown hook.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run()
            {
                pool.close();
                System.out.println("[IdServer] Shutting down server.");
            }
        });
    }

    /**
     * Binds the server with RMI using SSL communication.
     */
    private void bind() {
        try {
            RMIClientSocketFactory rmiClientSocketFactory = new SslRMIClientSocketFactory();
            RMIServerSocketFactory rmiServerSocketFactory = new SslRMIServerSocketFactory();
            Server ccAuth = (Server) UnicastRemoteObject.exportObject(this, 0, rmiClientSocketFactory, rmiServerSocketFactory);
            Registry registry = LocateRegistry.createRegistry(port);
            registry.rebind("//" + IP_HOST + ":" + port + "/" + SERVER_NAME, ccAuth);
        } catch (Exception e) {
            System.out.println("[IdServer] Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Prints a connection message if the server is verbose.
     */
    private void newConnection() {
        try {
            if (verbose) {
                System.out.println("[IdServer] Recieved connection from " + RemoteServer.getClientHost() + ".");
            }
        }
        catch (ServerNotActiveException e) {
            System.out.println("[IdServer] Error: " + e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized String create(String loginName, String realName, boolean fromCoordinator) throws RemoteException {
        newConnection();
        if (coordinator != null && !fromCoordinator) {
            return coordinator.create(loginName, realName);
        }
        try (Jedis jedis = pool.getResource()) {
            if (loginToUUID.containsKey(loginName)) {
                return "Error: Login name is already in use!";
            }
            Map<String, String> account = new HashMap<>();
            String uuid;
            do {
                uuid = UUID.randomUUID().toString();
            } while (jedis.exists(uuid));
            account.put("uuid", uuid);
            account.put("login_name", loginName);
            account.put("real_name", realName);
            account.put("ip_address", RemoteServer.getClientHost());
            account.put("creation_date", String.valueOf(java.time.LocalDateTime.now()));
            account.put("last_modified", String.valueOf(java.time.LocalDateTime.now()));
            jedis.hset(uuid, account);
            loginToUUID.put(loginName, uuid);
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] Created new account with the following info:\n\t" + account);
            }
            return "Account created with UUID " + uuid + ".";
        }
        catch (Exception e) {
            return "Error: Couldn't create account!";
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized String create(String loginName, String realName, String password, boolean fromCoordinator) throws RemoteException {
        newConnection();
        if (coordinator != null && !fromCoordinator) {
            return coordinator.create(loginName, realName, password);
        }
        try (Jedis jedis = pool.getResource()) {
            if (loginToUUID.containsKey(loginName)) {
                return "Error: Login name is already in use!";
            }
            Map<String, String> account = new HashMap<>();
            String uuid;
            do {
                uuid = UUID.randomUUID().toString();
            } while (jedis.exists(uuid));
            account.put("uuid", uuid);
            account.put("login_name", loginName);
            account.put("real_name", realName);
            account.put("ip_address", RemoteServer.getClientHost());
            account.put("creation_date", String.valueOf(java.time.LocalDateTime.now()));
            account.put("last_modified", String.valueOf(java.time.LocalDateTime.now()));
            account.put("password", password);
            jedis.hset(uuid, account);
            loginToUUID.put(loginName, uuid);
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] Created new account with the following info:\n\t" + account);
            }
            return "Account created with UUID " + uuid + ".";
        }
        catch (Exception e) {
            return "Error: Couldn't create account!";
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized String lookup(String loginName, boolean fromCoordinator) throws RemoteException {
        newConnection();
        if (coordinator != null && !fromCoordinator) {
            return coordinator.lookup(loginName);
        }
        try (Jedis jedis = pool.getResource()) {
            if (!loginToUUID.containsKey(loginName)) {
                return "Error: No account exists with the given login name!";
            }
            String uuid = loginToUUID.get(loginName);
            Map<String, String> account = jedis.hgetAll(uuid);
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] Found account with the following info:\n\t" + account);
            }
            account.remove("password");
            return "Found account matching login " + loginName + ":\n\t" + account.toString();
        }
        catch (Exception e) {
            return "Error: Couldn't lookup account!";
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized String reverseLookup(UUID id, boolean fromCoordinator) throws RemoteException {
        newConnection();
        if (coordinator != null && !fromCoordinator) {
            return coordinator.reverseLookup(id);
        }
        try (Jedis jedis = pool.getResource()) {
            if (!jedis.exists(id.toString())) {
                return "Error: No account exists with the given UUID!";
            }
            Map<String, String> account = jedis.hgetAll(id.toString());
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] Found account with the following info:\n\t" + account);
            }
            account.remove("password");
            return "Found account matching UUID " + id.toString() + ":\n\t" + account.toString();
        }
        catch (Exception e) {
            return "Error: Couldn't lookup account!";
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized String modify(String oldLoginName, String newLoginName, boolean fromCoordinator) throws RemoteException {
        newConnection();
        if (coordinator != null && !fromCoordinator) {
            return coordinator.modify(oldLoginName, newLoginName);
        }
        try (Jedis jedis = pool.getResource()) {
            if (!loginToUUID.containsKey(oldLoginName)) {
                return "Error: No account exists with the given login name!";
            }
            if (loginToUUID.containsKey(newLoginName)) {
                return "Error: New login name is already in use!";
            }
            String uuid = loginToUUID.get(oldLoginName);
            Map<String, String> account = jedis.hgetAll(uuid);
            if (account.containsKey("password")) {
                return "Error: Password is required to modify this account!";
            }
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] Modified account in the following way:\n\tOld: " + account);
            }
            account.put("login_name", newLoginName);
            account.put("last_modified", String.valueOf(java.time.LocalDateTime.now()));
            jedis.hset(uuid, account);
            loginToUUID.remove(oldLoginName);
            loginToUUID.put(newLoginName, uuid);
            if (verbose) {
                System.out.println("\tNew: " + account);
            }
            return "Modified login name of " + oldLoginName + " to be " + newLoginName + ".";
        }
        catch (Exception e) {
            return "Error: Couldn't modify account!";
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized String modify(String oldLoginName, String newLoginName, String password, boolean fromCoordinator) throws RemoteException {
        newConnection();
        if (coordinator != null && !fromCoordinator) {
            return coordinator.modify(oldLoginName, newLoginName, password);
        }
        try (Jedis jedis = pool.getResource()) {
            if (!loginToUUID.containsKey(oldLoginName)) {
                return "Error: No account exists with the given login name!";
            }
            if (loginToUUID.containsKey(newLoginName)) {
                return "Error: New login name is already in use!";
            }
            String uuid = loginToUUID.get(oldLoginName);
            Map<String, String> account = jedis.hgetAll(uuid);
            if (!account.containsKey("password")) {
                return "Error: No password is associated with this account!";
            }
            if (!password.equals(account.get("password"))) {
                return "Error: Incorrect password!";
            }
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] Modified account in the following way:\n\tOld: " + account);
            }
            account.put("login_name", newLoginName);
            account.put("last_modified", String.valueOf(java.time.LocalDateTime.now()));
            jedis.hset(uuid, account);
            loginToUUID.remove(oldLoginName);
            loginToUUID.put(newLoginName, uuid);
            if (verbose) {
                System.out.println("\tNew: " + account);
            }
            return "Modified login name of " + oldLoginName + " to be " + newLoginName + ".";
        }
        catch (Exception e) {
            return "Error: Couldn't modify account!";
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized String delete(String loginName, boolean fromCoordinator) throws RemoteException {
        newConnection();
        if (coordinator != null && !fromCoordinator) {
            return coordinator.delete(loginName);
        }
        try (Jedis jedis = pool.getResource()) {
            if (!loginToUUID.containsKey(loginName)) {
                return "Error: No account exists with the given login name!";
            }
            String uuid = loginToUUID.get(loginName);
            Map<String, String> account = jedis.hgetAll(uuid);
            if (account.containsKey("password")) {
                return "Error: Password is required to delete this account!";
            }
            jedis.del(uuid);
            loginToUUID.remove(loginName);
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] Deleted account with the following info:\n\t" + account);
            }
            return "Deleted account " + loginName + ".";
        }
        catch (Exception e) {
            return "Error: Couldn't delete account!";
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized String delete(String loginName, String password, boolean fromCoordinator) throws RemoteException {
        newConnection();
        if (coordinator != null && !fromCoordinator) {
            return coordinator.delete(loginName, password);
        }
        try (Jedis jedis = pool.getResource()) {
            if (!loginToUUID.containsKey(loginName)) {
                return "Error: No account exists with the given login name!";
            }
            String uuid = loginToUUID.get(loginName);
            Map<String, String> account = jedis.hgetAll(uuid);
            if (!account.containsKey("password")) {
                return "Error: No password is associated with this account!";
            }
            if (!password.equals(account.get("password"))) {
                return "Error: Incorrect password!";
            }
            jedis.del(uuid);
            loginToUUID.remove(loginName);
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] Deleted account with the following info:\n\t" + account);
            }
            return "Deleted account " + loginName + ".";
        }
        catch (Exception e) {
            return "Error: Couldn't delete account!";
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized String get(getType type, boolean fromCoordinator) throws RemoteException {
        newConnection();
        if (coordinator != null && !fromCoordinator) {
            return coordinator.get(type);
        }
        try (Jedis jedis = pool.getResource()) {
            Set<String> keys = jedis.keys("*");
            String string = "";
            switch (type) {
                case USERS:
                    string += "Found accounts with the following user names:";
                    for (String key : keys) {
                        string += "\n\t" + jedis.hget(key, "login_name");
                    }
                    break;
                case UUIDS:
                    string += "Found accounts with the following UUIDs:";
                    for (String key : keys) {
                        string += "\n\t" + key;
                    }
                    break;
                case ALL:
                    string += "Found accounts with the following information:";
                    for (String key : keys) {
                        Map<String, String> account = jedis.hgetAll(key);
                        account.remove("password");
                        string += "\n\t" + account;
                    }
                    break;
            }
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] " + string);
            }
            return string;
        }
        catch (Exception e) {
            return "Error: Couldn't get accounts!";
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized String getCoordinator() throws RemoteException {
        try {
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] Retrieved coordinator host name.");
            }
        }
        catch (ServerNotActiveException e) {}
        return coordinatorHost;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void setCoordinator(String coordinatorHost) throws RemoteException {
        this.coordinatorHost = coordinatorHost;
        coordinator = null;
        try {
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] Set coordinator as " + coordinatorHost + ".");
            }
        }
        catch (ServerNotActiveException e) {}
    }

    /**
     * {@inheritDoc}
     */
    public synchronized String election() throws RemoteException {
        try {
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] Starting election.");
            }
        }
        catch (ServerNotActiveException e) {}
        for (String hostName : serverList) {
            try {
                Registry registry = LocateRegistry.getRegistry(hostName, port);
                Server server = (Server) registry.lookup("//" + hostName + ":" + port + "/" + Server.SERVER_NAME);
                boolean isBully = server.isBully(IP_HOST);
                if (isBully) {
                    if (verbose) {
                        System.out.println("[IdServer] Election lost.");
                    }
                    return server.election();
                }
            }
            catch (RemoteException|NotBoundException e) {
                continue;
            }
        }
        if (verbose) {
            System.out.println("[IdServer] Election won.");
        }
        coordinatorHost = null;
        coordinator = new IdCoordinator(port, verbose);
        coordinator.announce();
        return IP_HOST;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized Map<String, Map<String, String>> getDatabase() throws RemoteException {
        Map<String, Map<String, String>> database = new HashMap<>();
        try (Jedis jedis = pool.getResource()) {
            Set<String> keys = jedis.keys("*");
            for (String key : keys) {
                database.put(key, jedis.hgetAll(key));
            }
        }
        try {
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] Successfully retrieved database.");
            }
        }
        catch (ServerNotActiveException e) {}
        return database;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized boolean isBully(String hostName) throws RemoteException {
        try {
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] Returned bully result: " + (hostName.hashCode() < IP_HOST.hashCode()) + ".");
            }
        }
        catch (ServerNotActiveException e) {}
        return hostName.hashCode() < IP_HOST.hashCode();
    }

    /**
     * Performs server boot up operations and starts election.
     */
    private void bootUp() {
        for (String hostName : serverList) {
            if (hostName.equals(IP_HOST)) {
                continue;
            }
            try {
                Registry registry = LocateRegistry.getRegistry(hostName, port);
                Server server = (Server) registry.lookup("//" + hostName + ":" + port + "/" + Server.SERVER_NAME);
                String getCoordinator = server.getCoordinator();
                if (getCoordinator == null) {
                    copyDatabase(hostName);
                    if (verbose) {
                        System.out.println("[IdServer] Retrieved database from coordinator " + hostName + ".");
                    }
                    break;
                }
                else {
                    try {
                        Registry coordinatorRegistry = LocateRegistry.getRegistry(getCoordinator, port);
                        Server coordinator = (Server) registry.lookup("//" + getCoordinator + ":" + port + "/" + Server.SERVER_NAME);
                        String testCoordinator = coordinator.getCoordinator();
                        if (testCoordinator == null) {
                            copyDatabase(getCoordinator);
                            if (verbose) {
                                System.out.println("[IdServer] Retrieved database from coordinator " + hostName + ".");
                            }
                            break;
                        }
                        else {
                            throw new RemoteException();
                        }
                    }
                    catch (RemoteException|NotBoundException e) {
                        copyDatabase(hostName);
                        if (verbose) {
                            System.out.println("[IdServer] Retrieved database from " + hostName + ".");
                        }
                    }
                }
            }
            catch (RemoteException|NotBoundException e) {
                System.err.println(e.getMessage());
            }
        }
        try {
            election();
        }
        catch (RemoteException e) {
            return;
        }
    }

    /**
     * Copies a database from a different server to this one.
     * @param hostName - host name of the server to be copied.
     * @throws RemoteException - in case of remote error.
     * @throws NotBoundException - in case of rmi error.
     */
    private void copyDatabase(String hostName) throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(hostName, port);
        Server server = (Server) registry.lookup("//" + hostName + ":" + port + "/" + Server.SERVER_NAME);
        Map<String, Map<String, String>> database = server.getDatabase();
        try (Jedis jedis = pool.getResource()) {
            for (String key : database.keySet()) {
                jedis.hset(key, database.get(key));
            }
            Set<String> keys = jedis.keys("*");
            for (String key : keys) {
                if (!database.containsKey(key)) {
                    jedis.del(key);
                }
            }
            if (verbose) {
                System.out.println("[IdServer/" + RemoteServer.getClientHost() + "] " + "Successfully copied database from " + hostName + ".");
            }
        }
        catch (Exception e) {
            return;
        }
    }

    /**
     * This class is used to perform the coordinator operations of the
     * distributed IdServers.
     */
    private class IdCoordinator {

        private boolean verbose;
        private int port;

        /**
         * Creates a new IdCoordinator.
         * @param port - port number of the coordinator.
         * @param verbose - whether explicit operations are output.
         */
        public IdCoordinator(int port, boolean verbose) {
            this.port = port;
            this.verbose = verbose;
        }

        /**
         * Broadcasts create message to all servers.
         * @param loginName - unique login name.
         * @param realName - real name of the user.
         * @return - server response message.
         * @throws RemoteException - in case of remote error.
         */
        public String create(String loginName, String realName) throws RemoteException {
            for (String hostName : serverList) {
                if (hostName.equals(IP_HOST)) {
                    continue;
                }
                Thread thread = new Thread(() -> {
                    try {
                        Registry registry = LocateRegistry.getRegistry(hostName, port);
                        Server server = (Server) registry.lookup("//" + hostName + ":" + port + "/" + Server.SERVER_NAME);
                        server.create(loginName, realName, true);
                    }
                    catch (RemoteException|NotBoundException e) {
                        System.err.println(e.getMessage());
                    }
                });
            }
            if (verbose) {
                System.out.println("[IdCoordinator] Create broadcast sent to all servers.");
            }
            try {
                Registry registry = LocateRegistry.getRegistry(IP_HOST, port);
                Server server = (Server) registry.lookup("//" + IP_HOST + ":" + port + "/" + Server.SERVER_NAME);
                return server.create(loginName, realName, true);
            }
            catch (RemoteException|NotBoundException e) {
                System.err.println(e.getMessage());
            }
            return null;
        }

        /**
         * Broadcasts create message to all servers.
         * @param loginName - unique login name.
         * @param realName - real name of the user.
         * @return - server response message.
         */
        public String create(String loginName, String realName, String password) {
            for (String hostName : serverList) {
                if (hostName.equals(IP_HOST)) {
                    continue;
                }
                Thread thread = new Thread(() -> {
                    try {
                        Registry registry = LocateRegistry.getRegistry(hostName, port);
                        Server server = (Server) registry.lookup("//" + hostName + ":" + port + "/" + Server.SERVER_NAME);
                        server.create(loginName, realName, password, true);
                    }
                    catch (RemoteException|NotBoundException e) {};
                });
            }
            if (verbose) {
                System.out.println("[IdCoordinator] Create broadcast sent to all servers.");
            }
            try {
                Registry registry = LocateRegistry.getRegistry(IP_HOST, port);
                Server server = (Server) registry.lookup("//" + IP_HOST + ":" + port + "/" + Server.SERVER_NAME);
                return server.create(loginName, realName, password, true);
            }
            catch (RemoteException|NotBoundException e) {
                System.err.println(e.getMessage());
            }
            return null;
        }

        /**
         * Broadcasts lookup message to all servers.
         * @param loginName - unique login name.
         * @return - server response message.
         */
        public String lookup(String loginName) {
            for (String hostName : serverList) {
                if (hostName.equals(IP_HOST)) {
                    continue;
                }
                Thread thread = new Thread(() -> {
                    try {
                        Registry registry = LocateRegistry.getRegistry(hostName, port);
                        Server server = (Server) registry.lookup("//" + hostName + ":" + port + "/" + Server.SERVER_NAME);
                        server.lookup(loginName, true);
                    }
                    catch (RemoteException|NotBoundException e) {};
                });
            }
            if (verbose) {
                System.out.println("[IdCoordinator] Lookup broadcast sent to all servers.");
            }
            try {
                Registry registry = LocateRegistry.getRegistry(IP_HOST, port);
                Server server = (Server) registry.lookup("//" + IP_HOST + ":" + port + "/" + Server.SERVER_NAME);
                return server.lookup(loginName, true);
            }
            catch (RemoteException|NotBoundException e) {
                System.err.println(e.getMessage());
            }
            return null;
        }

        /**
         * Broadcasts reverse lookup message to all servers.
         * @param id - UUID of user.
         * @return - server response message.
         */
        public String reverseLookup(UUID id) {
            for (String hostName : serverList) {
                if (hostName.equals(IP_HOST)) {
                    continue;
                }
                Thread thread = new Thread(() -> {
                    try {
                        Registry registry = LocateRegistry.getRegistry(hostName, port);
                        Server server = (Server) registry.lookup("//" + hostName + ":" + port + "/" + Server.SERVER_NAME);
                        server.reverseLookup(id, true);
                    }
                    catch (RemoteException|NotBoundException e) {
                        System.err.println(e.getMessage());
                    }
                });
            }
            if (verbose) {
                System.out.println("[IdCoordinator] Reverse lookup broadcast sent to all servers.");
            }
            try {
                Registry registry = LocateRegistry.getRegistry(IP_HOST, port);
                Server server = (Server) registry.lookup("//" + IP_HOST + ":" + port + "/" + Server.SERVER_NAME);
                return server.reverseLookup(id, true);
            }
            catch (RemoteException|NotBoundException e) {
                System.err.println(e.getMessage());
            }
            return null;
        }

        /**
         * Broadcasts modification message to all servers.
         * @param oldLoginName - original login name.
         * @param newLoginName - login name to change to.
         * @return - server response message.
         */
        public String modify(String oldLoginName, String newLoginName) {
            for (String hostName : serverList) {
                if (hostName.equals(IP_HOST)) {
                    continue;
                }
                Thread thread = new Thread(() -> {
                    try {
                        Registry registry = LocateRegistry.getRegistry(hostName, port);
                        Server server = (Server) registry.lookup("//" + hostName + ":" + port + "/" + Server.SERVER_NAME);
                        server.modify(oldLoginName, newLoginName, true);
                    }
                    catch (RemoteException|NotBoundException e) {};
                });
            }
            if (verbose) {
                System.out.println("[IdCoordinator] Modify broadcast sent to all servers.");
            }
            try {
                Registry registry = LocateRegistry.getRegistry(IP_HOST, port);
                Server server = (Server) registry.lookup("//" + IP_HOST + ":" + port + "/" + Server.SERVER_NAME);
                return server.modify(oldLoginName, newLoginName, true);
            }
            catch (RemoteException|NotBoundException e) {
                System.err.println(e.getMessage());
            }
            return null;
        }

        /**
         * Broadcasts modification message to all servers.
         * @param oldLoginName - original login name.
         * @param newLoginName - login name to change to.
         * @return - server response message.
         */
        public String modify(String oldLoginName, String newLoginName, String password) {
            for (String hostName : serverList) {
                if (hostName.equals(IP_HOST)) {
                    continue;
                }
                Thread thread = new Thread(() -> {
                    try {
                        Registry registry = LocateRegistry.getRegistry(hostName, port);
                        Server server = (Server) registry.lookup("//" + hostName + ":" + port + "/" + Server.SERVER_NAME);
                        server.modify(oldLoginName, newLoginName, password, true);
                    }
                    catch (RemoteException|NotBoundException e) {};
                });
            }
            if (verbose) {
                System.out.println("[IdCoordinator] Modify broadcast sent to all servers.");
            }
            try {
                Registry registry = LocateRegistry.getRegistry(IP_HOST, port);
                Server server = (Server) registry.lookup("//" + IP_HOST + ":" + port + "/" + Server.SERVER_NAME);
                return server.modify(oldLoginName, newLoginName, password, true);
            }
            catch (RemoteException|NotBoundException e) {
                System.err.println(e.getMessage());
            }
            return null;
        }

        /**
         * Broadcasts the delete message to all servers.
         * @param loginName - unique login name.
         * @return - server response message.
         */
        public String delete(String loginName) {
            for (String hostName : serverList) {
                if (hostName.equals(IP_HOST)) {
                    continue;
                }
                Thread thread = new Thread(() -> {
                    try {
                        Registry registry = LocateRegistry.getRegistry(hostName, port);
                        Server server = (Server) registry.lookup("//" + hostName + ":" + port + "/" + Server.SERVER_NAME);
                        server.delete(loginName, true);
                    }
                    catch (RemoteException|NotBoundException e) {};
                });
            }
            if (verbose) {
                System.out.println("[IdCoordinator] Delete broadcast sent to all servers.");
            }
            try {
                Registry registry = LocateRegistry.getRegistry(IP_HOST, port);
                Server server = (Server) registry.lookup("//" + IP_HOST + ":" + port + "/" + Server.SERVER_NAME);
                return server.delete(loginName, true);
            }
            catch (RemoteException|NotBoundException e) {
                System.err.println(e.getMessage());
            }
            return null;
        }

        /**
         * Broadcasts the delete message to all servers.
         * @param loginName - unique login name.
         * @return - server response message.
         */
        public String delete(String loginName, String password) {
            for (String hostName : serverList) {
                if (hostName.equals(IP_HOST)) {
                    continue;
                }
                Thread thread = new Thread(() -> {
                    try {
                        Registry registry = LocateRegistry.getRegistry(hostName, port);
                        Server server = (Server) registry.lookup("//" + hostName + ":" + port + "/" + Server.SERVER_NAME);
                        server.delete(loginName, password, true);
                    }
                    catch (RemoteException|NotBoundException e) {};
                });
            }
            if (verbose) {
                System.out.println("[IdCoordinator] Delete broadcast sent to all servers.");
            }
            try {
                Registry registry = LocateRegistry.getRegistry(IP_HOST, port);
                Server server = (Server) registry.lookup("//" + IP_HOST + ":" + port + "/" + Server.SERVER_NAME);
                return server.delete(loginName, password, true);
            }
            catch (RemoteException|NotBoundException e) {
                System.err.println(e.getMessage());
            }
            return null;
        }

        /**
         * Broadcasts the get message to all servers.
         * @param type - type of data to retrieve.
         * @return - server response message.
         */
        public String get(getType type) {
            for (String hostName : serverList) {
                if (hostName.equals(IP_HOST)) {
                    continue;
                }
                Thread thread = new Thread(() -> {
                    try {
                        Registry registry = LocateRegistry.getRegistry(hostName, port);
                        Server server = (Server) registry.lookup("//" + hostName + ":" + port + "/" + Server.SERVER_NAME);
                        server.get(type, true);
                    }
                    catch (RemoteException|NotBoundException e) {};
                });
            }
            if (verbose) {
                System.out.println("[IdCoordinator] Get broadcast sent to all servers.");
            }
            try {
                Registry registry = LocateRegistry.getRegistry(IP_HOST, port);
                Server server = (Server) registry.lookup("//" + IP_HOST + ":" + port + "/" + Server.SERVER_NAME);
                return server.get(type, true);
            }
            catch (RemoteException|NotBoundException e) {
                System.err.println(e.getMessage());
            }
            return null;
        }

        /**
         * Announces the coordinator's election to all servers.
         */
        public void announce() {
            for (String hostName : serverList) {
                if (hostName.equals(IP_HOST)) {
                    continue;
                }
                Thread thread = new Thread(() -> {
                    try {
                        Registry registry = LocateRegistry.getRegistry(hostName, port);
                        Server server = (Server) registry.lookup("//" + hostName + ":" + port + "/" + Server.SERVER_NAME);
                        server.setCoordinator(IP_HOST);
                    }
                    catch (RemoteException|NotBoundException e) {};
                });
            }
            if (verbose) {
                System.out.println("[IdCoordinator] Coordinator announcement sent to all servers.");
            }
        }
    }
}