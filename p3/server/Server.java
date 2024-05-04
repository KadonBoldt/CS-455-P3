package server;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

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
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String create(String loginName, String realName) throws RemoteException;
    /**
     * Creates an account for the provided credentials and adds it to the database.
     * @param loginName - unique login name.
     * @param realName - real name of the user.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String create(String loginName, String realName, String password) throws RemoteException;

    /**
     * Looks up an account in the database using login name and retrieves its information.
     * @param loginName - unique login name.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String lookup(String loginName) throws RemoteException;

    /**
     * Looks up an account in the database using UUID and retrieves its information.
     * @param id - UUID of user.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String reverseLookup(UUID id) throws RemoteException;

    /**
     * Modifies an account's login name to the new provided name.
     * @param oldLoginName - original login name.
     * @param newLoginName - login name to change to.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String modify(String oldLoginName, String newLoginName) throws RemoteException;
    /**
     * Modifies an account's login name to the new provided name.
     * @param oldLoginName - original login name.
     * @param newLoginName - login name to change to.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String modify(String oldLoginName, String newLoginName, String password) throws RemoteException;

    /**
     * Deletes the identified account from the database.
     * @param loginName - unique login name.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String delete(String loginName) throws RemoteException;
    /**
     * Deletes the identified account from the database.
     * @param loginName - unique login name.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String delete(String loginName, String password) throws RemoteException;

    /**
     * Retrieves data on the accounts stored within the database.
     * @param type - type of data to retrieve.
     * @return - server response message.
     * @throws RemoteException - in case of remote error.
     */
    public String get(getType type) throws RemoteException;

    /**
     * Returns whether the server is the coordinator or not.
     * @return - "yes" if server is coordinator, or the IP address of coordinator if not.
     * @throws RemoteException - in case of remote error.
     */
    public String getCoordinator() throws RemoteException;

    /**
     * Starts an election for a coordinator.
     * @return - host name of new coordinator.
     * @throws RemoteException - in case or remote error.
     */
    public String startElection() throws RemoteException;
}
