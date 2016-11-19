/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.identity.mgt.user.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.mgt.exception.UserManagerException;
import org.wso2.carbon.identity.mgt.user.ConnectedGroup;
import org.wso2.carbon.identity.mgt.user.UniqueIdResolver;
import org.wso2.carbon.identity.mgt.user.UniqueUser;
import org.wso2.carbon.identity.mgt.user.UserPartition;
import org.wso2.carbon.identity.mgt.util.NamedPreparedStatement;
import org.wso2.carbon.identity.mgt.util.UnitOfWork;
import org.wso2.carbon.identity.mgt.util.UserManagerConstants;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/**
 * UserManager implementation.
 */
public class UniqueIdResolverImpl implements UniqueIdResolver {

    private static Logger log = LoggerFactory.getLogger(UniqueIdResolverImpl.class);

    //TODO initialize the datasource
    private DataSource dataSource;

    public UniqueIdResolverImpl() throws UserManagerException {

    }

    @Override
    public UniqueUser getUniqueUser(String connectorUserId, String connectorId) throws UserManagerException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {
            final String selectUserUuid = "SELECT USER_UUID, CONNECTOR_TYPE, CONNECTOR_ID, CONNECTOR_USER_ID FROM " +
                    "IDM_ENTITY WHERE USER_UUID = ( " +
                    "SELECT USER_UUID FROM IDM_ENTITY " +
                    "WHERE CONNECTOR_USER_ID = :connector_user_id; " +
                    "AND CONNECTOR_ID = :connector_id;)";

            String userUUID = null;
            UniqueUser uniqueUser = new UniqueUser();
            List<UserPartition> userPartitions = new ArrayList<>();
            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(
                    unitOfWork.getConnection(),
                    selectUserUuid);
            namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.CONNECTOR_USER_ID, connectorUserId);
            namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.CONNECTOR_ID, connectorId);
            try (ResultSet resultSet = namedPreparedStatement.getPreparedStatement().executeQuery()) {

                while (resultSet.next()) {
                    UserPartition userPartition = new UserPartition();
                    userUUID = resultSet.getString(UserManagerConstants.DatabaseColumnNames.USER_UUID);
                    userPartition.setConnectorId(resultSet.getString(UserManagerConstants.DatabaseColumnNames
                            .CONNECTOR_ID));
                    userPartition.setConnectorUserId(resultSet.getString(UserManagerConstants.DatabaseColumnNames
                            .CONNECTOR_USER_ID));
                    userPartition.setIdentityStore(UserManagerConstants.IDENTITY_STORE_CONNECTOR.equals(resultSet
                            .getString(UserManagerConstants.DatabaseColumnNames.CONNECTOR_TYPE)));
                    userPartitions.add(userPartition);
                }
            }
            if (userUUID == null) {
                throw new UserManagerException("No user found.");
            }
            uniqueUser.setUniqueUserId(userUUID);
            uniqueUser.setUserPartitions(userPartitions);
            return uniqueUser;

        } catch (SQLException e) {
            throw new UserManagerException("Error while searching user.", e);
        }
    }

    @Override
    public boolean isUserExists(String uniqueUserId) throws UserManagerException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {
            final String selectUser = "SELECT ID FROM IDM_ENTITY " +
                    "WHERE USER_UUID = :user_uuid;";

            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(
                    unitOfWork.getConnection(),
                    selectUser);
            namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.USER_UUID, uniqueUserId);
            try (ResultSet resultSet = namedPreparedStatement.getPreparedStatement().executeQuery()) {

                if (resultSet.next()) {
                    return true;
                } else {
                    return false;
                }
            }

        } catch (SQLException e) {
            throw new UserManagerException("Error while searching user.", e);
        }
    }

    @Override
    public String getConnectorUserId(String uniqueUserId, String connectorId) throws UserManagerException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {
            final String selectUserUuid = "SELECT CONNECTOR_USER_ID FROM IDM_ENTITY " +
                    "WHERE USER_UUID = :user_uuid; " +
                    "AND CONNECTOR_ID = :connector_id;";

            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(
                    unitOfWork.getConnection(),
                    selectUserUuid);
            namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.USER_UUID, uniqueUserId);
            namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.CONNECTOR_ID, connectorId);
            try (ResultSet resultSet = namedPreparedStatement.getPreparedStatement().executeQuery()) {

                if (resultSet.next()) {
                    return resultSet.getString(UserManagerConstants.DatabaseColumnNames.CONNECTOR_USER_ID);
                } else {
                    throw new UserManagerException("User not found.");
                }
            }

        } catch (SQLException e) {
            throw new UserManagerException("Error while searching user.", e);
        }
    }

    @Override
    public void addUser(UniqueUser uniqueUser, String domainName) throws
            UserManagerException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {
            final String addUser = "INSERT INTO IDM_ENTITY " +
                    "(USER_UUID, CONNECTOR_USER_ID, CONNECTOR_ID, DOMAIN, CONNECTOR_TYPE) " +
                    "VALUES (:user_uuid;, :connector_user_id;, :connector_id;, :domain;, :connector_type;)";
            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(
                    unitOfWork.getConnection(), addUser);
            for (UserPartition userPartition : uniqueUser.getUserPartitions()) {
                namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.USER_UUID, uniqueUser
                        .getUniqueUserId());
                namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.CONNECTOR_USER_ID,
                        userPartition.getConnectorUserId());
                namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.CONNECTOR_ID,
                        userPartition.getConnectorId());
                namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.DOMAIN, domainName);
                namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.CONNECTOR_TYPE,
                        userPartition.isIdentityStore() ? UserManagerConstants.IDENTITY_STORE_CONNECTOR :
                                UserManagerConstants.CREDENTIAL_STORE_CONNECTOR);
                namedPreparedStatement.getPreparedStatement().addBatch();
            }

            namedPreparedStatement.getPreparedStatement().executeBatch();
        } catch (SQLException e) {
            throw new UserManagerException("Error while adding user.", e);
        }
    }

    @Override
    public void addUsers(Map<String, List<UserPartition>> connectedUsersMap) throws UserManagerException {

    }

    @Override
    public void updateUser(String uniqueUserId, Map<String, String> connectorUserIdMap) throws UserManagerException {

        //TODO need to check whether the entry is already there before updating. else need to add
        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {
            final String updateUser = "UPDATE IDM_ENTITY " +
                    "SET CONNECTOR_USER_ID = :connector_user_id;" +
                    "WHERE USER_UUID = :user_uuid; AND CONNECTOR_ID = :connector_id;";
            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(
                    unitOfWork.getConnection(), updateUser);
            for (Map.Entry<String, String> entry : connectorUserIdMap.entrySet()) {
                namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.USER_UUID, uniqueUserId);
                namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.CONNECTOR_USER_ID,
                        entry.getValue());
                namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.CONNECTOR_ID,
                        entry.getKey());
                namedPreparedStatement.getPreparedStatement().addBatch();
            }
            namedPreparedStatement.getPreparedStatement().executeBatch();
        } catch (SQLException e) {
            throw new UserManagerException("Error while adding user.", e);
        }
    }

    @Override
    public void deleteUser(String uniqueUserId) throws UserManagerException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {
            final String deleteUser = "DELETE FROM IDM_ENTITY " +
                    "WHERE USER_UUID = :user_uuid;";
            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(
                    unitOfWork.getConnection(), deleteUser);
            namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.USER_UUID, uniqueUserId);

            namedPreparedStatement.getPreparedStatement().executeUpdate();
        } catch (SQLException e) {
            throw new UserManagerException("Error while adding user.", e);
        }
    }

    @Override
    public Map<String, String> getConnectorUserIds(String userUniqueId) throws UserManagerException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {
            final String selectUserUuid = "SELECT CONNECTOR_ID, CONNECTOR_USER_ID FROM " +
                    "IDM_ENTITY WHERE USER_UUID = :user_uuid;";

            Map<String, String> connectorUserIds = new HashMap<>();
            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(
                    unitOfWork.getConnection(),
                    selectUserUuid);
            namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.USER_UUID, userUniqueId);
            try (ResultSet resultSet = namedPreparedStatement.getPreparedStatement().executeQuery()) {

                while (resultSet.next()) {
                    String connectorId = resultSet.getString(
                            UserManagerConstants.DatabaseColumnNames.CONNECTOR_ID);
                    String connectorUserId = resultSet.getString(
                            UserManagerConstants.DatabaseColumnNames.CONNECTOR_USER_ID);
                    connectorUserIds.put(connectorId, connectorUserId);
                }
            }

            return connectorUserIds;

        } catch (SQLException e) {
            throw new UserManagerException("Error while searching user.", e);
        }
    }

    @Override
    public String getDomainNameFromUserUniqueId(String uniqueUserId) throws UserManagerException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {
            //TODO Do we need to limit 1 result?
            final String selectUserUuid = "SELECT DOMAIN FROM " +
                    "IDM_ENTITY WHERE USER_UUID = :user_uuid;";

            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(
                    unitOfWork.getConnection(),
                    selectUserUuid);
            namedPreparedStatement.setString(UserManagerConstants.SQLPlaceholders.USER_UUID, uniqueUserId);
            try (ResultSet resultSet = namedPreparedStatement.getPreparedStatement().executeQuery()) {

                if (resultSet.next()) {
                    return resultSet.getString(UserManagerConstants.DatabaseColumnNames.DOMAIN);
                } else {
                    throw new UserManagerException("User not found with the given user id.");
                }
            }

        } catch (SQLException e) {
            throw new UserManagerException("Error while searching user.", e);
        }
    }

    @Override
    public String getDomainNameFromGroupUniqueId(String uniqueGroupId) throws UserManagerException {
        return null;
    }

    @Override
    public Map<String, String> getConnectorGroupIds(String uniqueGroupId) throws UserManagerException {
        return null;
    }

    @Override
    public void addGroup(String uniqueGroupId, List<ConnectedGroup> connectedGroups) throws UserManagerException {

    }

    @Override
    public void addGroups(Map<String, List<ConnectedGroup>> connectedGroupsMap) throws UserManagerException {

    }

    @Override
    public void updateGroup(String uniqueGroupId, Map<String, String> connectorGroupIdMap) throws UserManagerException {

    }

    @Override
    public void deleteGroup(String uniqueGroupId) throws UserManagerException {

    }

    @Override
    public String getUniqueGroupId(String connectorGroupId, String connectorId) throws UserManagerException {
        return null;
    }

    @Override
    public boolean isGroupExists(String uniqueGroupId) throws UserManagerException {
        return false;
    }

    @Override
    public void updateGroupsOfUser(String uniqueUserId, List<String> uniqueGroupIds) throws UserManagerException {

    }

    @Override
    public void updateGroupsOfUser(String uniqueUserId, List<String> uniqueGroupIdsToUpdate, List<String>
            uniqueGroupIdsToRemove) throws UserManagerException {

    }

    @Override
    public void updateUsersOfGroup(String uniqueGroupId, List<String> uniqueUserIds) throws UserManagerException {

    }

    @Override
    public void updateUsersOfGroup(String uniqueGroupId, List<String> uniqueUserIdsToUpdate, List<String>
            uniqueUserIdsToRemove) throws UserManagerException {

    }
}
