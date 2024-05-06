package server;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This interface serves as the Remote interface for the
 * IdServer program.
 *
 * @author Kadon Boldt
 * @version 1.0
 * @since 4/10/2024
 */
public interface Server extends Remote {

    // Global variables for IdServer and IdClient.
    public static final int DEFAULT_PORT = 1099;
    public static final int JEDIS_PORT = 5151;
    public static final String SERVER_NAME = "IdServer";
    public static final String SSL_PASSWORD = "password123";

    // Used in get().
    public enum getType {
        USERS,
        UUIDS,
        ALL;
    }

    /**
     * Creates an account for the provided credentials and adds it to the database.
     * @param loginName - unique login name.
     * @param realName - real name of the user.
     * @param timestamp - Lamport timestamp of the sender.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String create(String loginName, String realName, LamportTimestamp timestamp) throws RemoteException;
    /**
     * Creates an account for the provided credentials and adds it to the database.
     * @param loginName - unique login name.
     * @param realName - real name of the user.
     * @param password - password for the account.
     * @param timestamp - Lamport timestamp of the sender.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String create(String loginName, String realName, String password, LamportTimestamp timestamp) throws RemoteException;

    /**
     * Looks up an account in the database using login name and retrieves its information.
     * @param loginName - unique login name.
     * @param timestamp - Lamport timestamp of the sender.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String lookup(String loginName, LamportTimestamp timestamp) throws RemoteException;

    /**
     * Looks up an account in the database using UUID and retrieves its information.
     * @param id - UUID of user.
     * @param timestamp - Lamport timestamp of the sender.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String reverseLookup(UUID id, LamportTimestamp timestamp) throws RemoteException;

    /**
     * Modifies an account's login name to the new provided name.
     * @param oldLoginName - original login name.
     * @param newLoginName - login name to change to.
     * @param timestamp - Lamport timestamp of the sender.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String modify(String oldLoginName, String newLoginName, LamportTimestamp timestamp) throws RemoteException;
    /**
     * Modifies an account's login name to the new provided name.
     * @param oldLoginName - original login name.
     * @param newLoginName - login name to change to.
     * @param password - password for the account.
     * @param timestamp - Lamport timestamp of the sender.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String modify(String oldLoginName, String newLoginName, String password, LamportTimestamp timestamp) throws RemoteException;

    /**
     * Deletes the identified account from the database.
     * @param loginName - unique login name.
     * @param timestamp - Lamport timestamp of the sender.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String delete(String loginName, LamportTimestamp timestamp) throws RemoteException;
    /**
     * Deletes the identified account from the database.
     * @param loginName - unique login name.
     * @param timestamp - Lamport timestamp of the sender.
     * @param password - password for the account.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String delete(String loginName, String password, LamportTimestamp timestamp) throws RemoteException;

    /**
     * Retrieves data on the accounts stored within the database.
     * @param type - type of data to retrieve.
     * @param timestamp - Lamport timestamp of the sender.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String get(getType type, LamportTimestamp timestamp) throws RemoteException;

    /**
     * Returns whether the server is the coordinator or not.
     * @return - "yes" if server is coordinator, or the IP address of coordinator if not.
     * @throws RemoteException - in case of remote error.
     */
    public String getCoordinator() throws RemoteException;

    /**
     * Sets coordinator when an election occurs.
     * @param coordinatorHost - IP host name of new coordinator.
     * @throws RemoteException - in case of remote error.
     */
    public void setCoordinator(String coordinatorHost) throws RemoteException;

    /**
     * Starts an election for a coordinator.
     * @return - host name of new coordinator.
     * @throws RemoteException - in case or remote error.
     */
    public String election() throws RemoteException;

    /**
     * Exports the server's redis database.
     * @return - database mapping of redis server.
     * @throws RemoteException - in case of remote error.
     */
    public Map<String, Map<String, String>> getDatabase() throws RemoteException;

    /**
     * Returns whether or not the server has an ID higher than the provided one.
     * @return - whether the server has a higher ID that the one given.
     * @throws RemoteException - in case of remote error.
     */
    public boolean isBully(String id) throws RemoteException;

    /**
     * Implementation of a lamport timestamp.
     */
    public class LamportTimestamp implements Serializable {

        private AtomicInteger clock = new AtomicInteger(0);

        /**
         * Syncs the current clock to a different clock.
         * @param timestamp - timestamp to sync to.
         * @return - whether the clocks were in sync.
         */
        public boolean sync(LamportTimestamp timestamp) {
            boolean inSync = clock.get() == timestamp.clock.get();
            clock.set(timestamp.clock.get() + 1);
            return inSync;
        }

        /**
         * Increments the atomic clock.
         */
        public void increment() {
            clock.incrementAndGet();
        }

        /**
         * Gets the current value of the clock.
         */
        public int get() {
            return clock.get();
        }
    }
}