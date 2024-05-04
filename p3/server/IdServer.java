package server;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.*;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    /**
     * Main driver of the server-side program.
     * @param args - command line arguments.
     */
    public static void main(String[] args) {
        System.setProperty("javax.net.ssl.keyStore", "resources/Server_Keystore");
        System.setProperty("javax.net.ssl.keyStorePassword", SSL_PASSWORD);
        System.setProperty("java.security.policy", "resources/mysecurity.policy");
        int port = DEFAULT_PORT;
        boolean verbose = false;
        try {
            switch (args.length) {
                case 0:
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
            e.printStackTrace();
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Prints usage statement then exits the program.
     * @param code - exit code.
     */
    public static void showUsage(int code) {
        System.out.println("Usage: IdServer [--numport/-n <port#>] [--verbose/-v]");
        System.exit(code);
    }

    private int port;
    private boolean verbose;
    private JedisPool pool;
    private Map<String, String> loginToUUID = new HashMap<>();
    private String coordinator = null;

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
            registry.rebind("//localhost:" + port + "/" + SERVER_NAME, ccAuth);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
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
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized String create(String loginName, String realName) throws RemoteException {
        newConnection();
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
    public synchronized String create(String loginName, String realName, String password) throws RemoteException {
        newConnection();
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
    public synchronized String lookup(String loginName) throws RemoteException {
        newConnection();
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
    public synchronized String reverseLookup(UUID id) throws RemoteException {
        newConnection();
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
    public synchronized String modify(String oldLoginName, String newLoginName) throws RemoteException {
        newConnection();
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
    public synchronized String modify(String oldLoginName, String newLoginName, String password) throws RemoteException {
        newConnection();
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
    public synchronized String delete(String loginName) throws RemoteException {
        newConnection();
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
    public synchronized String delete(String loginName, String password) throws RemoteException {
        newConnection();
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
    public synchronized String get(getType type) throws RemoteException {
        newConnection();
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
    public String getCoordinator() throws RemoteException {
        return coordinator;
    }

    /**
     * {@inheritDoc}
     */
    public String startElection() throws RemoteException {
        return "localhost";
    }
}
