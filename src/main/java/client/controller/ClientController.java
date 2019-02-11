package client.controller;

import client.model.Group;
import client.model.ServerResponse;
import client.model.formatMsgWithServer.*;
import client.utils.Connector;
import client.utils.HTTPSRequest;
import client.utils.Sound;
import client.view.ChatViewController;
import client.view.customFX.CFXListElement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import database.dao.DataBaseService;
import database.entity.Message;
import database.entity.User;
import javafx.scene.control.Alert;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static client.utils.Common.showAlert;

public class ClientController {
    private static final Logger controllerLogger = LogManager.getLogger(ClientController.class);
    private static ClientController instance;
    private static String token;
    private ChatViewController chatViewController;

    private User receiver;
    private User myUser;
    private Connector conn;
    private List<Long> contactList;
    private List<CFXListElement> contactListOfCards;

    private DataBaseService dbService;

    public void setChatViewController(ChatViewController chatViewController) {
        this.chatViewController = chatViewController;
    }

    private ClientController() {
        receiver = null;
        myUser = null;
        conn = null;
    }

    public User getMyUser() {
        return myUser;
    }

    public User getReciever() {
        return receiver;
    }

    public static ClientController getInstance() {
        if (instance == null) {
            instance = new ClientController();
        }
        return instance;
    }

    private void connect(String token) {
        conn = new Connector(token, getInstance());
    }

    public String getSenderName() {
        return myUser.getAccount_name();
    }

    public String getRecieverName() {
        return receiver.getAccount_name();
    }

    public void setReceiver(long receiverId) {
        this.receiver = dbService.getUser(receiverId);
        loadChat();
    }

    public void setReceiver(String receiverName) {
        this.receiver = dbService.getUser(receiverName);
        loadChat();
    }

    public void setReceiver(User receiver) {
        if (!contactList.contains(receiver.getUid()))
            addContactToDbAndChat(receiver); // todo поправить логику получения сообщений?
            //NB: todo добавлять в список на сервере? addContactToDbAndChat не добавляет
        this.receiver = receiver;
        loadChat();
    }

    public boolean hasReceiver(long receiverId) {
        return dbService.getUser(receiverId) != null;
    }

    public boolean hasReceiver(String receiverName) {
        return dbService.getUser(receiverName) != null;
    }

    public List<CFXListElement> getContactListOfCards() {
        return contactListOfCards;
    }

    private boolean authentication(String login, String password) {
        if (!login.isEmpty() && !password.isEmpty()) {
            String answer = "0";
            AuthToServer ATS = new AuthToServer(login, password);
            String reqJSON = new Gson().toJson(ATS);
            try {
                answer = HTTPSRequest.authorization(reqJSON);
            } catch (Exception e) {
                controllerLogger.error("HTTPSRequest.authorization_error", e);
            }
            if (answer.contains("token")) {
                GsonBuilder builder = new GsonBuilder();
                Gson gson = builder.create();
                AuthFromServer AFS = gson.fromJson(answer, AuthFromServer.class);
                System.out.println(" answer server " + AFS.token);
                token = AFS.token;
                connect(token);

                try {
                    ServerResponse response = HTTPSRequest.getMySelf(token);
                    myUser = convertJSONToUser(response.getResponseJson());
                    System.out.println("info myUser id "+myUser.getUid()+
                            " acc name "+myUser.getAccount_name());
                } catch (Exception e) {
                    controllerLogger.error("HTTPSRequest.getMySelf_error", e);
                }
                myUser.setAccount_name(login);
                synchronizeContactList();

                return true;
            } else {
//                showAlert("Ошибка авторизации!", Alert.AlertType.ERROR);
                controllerLogger.info("Ошибка авторизации!", Alert.AlertType.ERROR);
            }
        } else {
//            showAlert("Неполные данные для авторизации!", Alert.AlertType.ERROR);
            controllerLogger.info("Неполные данные для авторизации!", Alert.AlertType.ERROR);
            return false;
        }
        return false;
    }

    private MessageFromServer convertMessageToMFS(String jsonText) {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        return gson.fromJson(jsonText, MessageFromServer.class);
    }

    public void receiveMessage(String message) {
        MessageFromServer mfs = convertMessageToMFS(message);
        if (!contactList.contains(mfs.getSenderid())) { //Проверяем, что осообщение пришло не от клиента в списке
            try {
                ServerResponse response = HTTPSRequest.getUser(mfs.getSenderid(), token);
                switch (response.getResponseCode()) {
                    case 200:
                        addContact(convertJSONToUser(response.getResponseJson()).getEmail());
                        break;
                    case 404:
                        showAlert("Пользователь не найден", Alert.AlertType.ERROR);//с id: " + mfs.getSenderid() + "
                        break;
                    default:
                        showAlert("Общая ошибка!", Alert.AlertType.ERROR);
                }
            } catch (Exception e) {
                controllerLogger.error("HTTPSRequest.getUser_error", e);
            }
        }
        //Проверяем что у нас чат именно с этим пользователем, иначе сообщение не выводится
        //Как будет с группами пока не понятно
        if (receiver.getUid() == mfs.getSenderid()) {
            chatViewController.showMessage(mfs.getSender_name(), mfs.getMessage(), mfs.getTimestamp(), true);
        }
        if (mfs.getSenderid() !=0) { //отключаем звук для служебных сообщений
            Sound.playSoundNewMessage().join(); //Звук нового сообщения должен быть в любом случае
        }
        dbService.addMessage(mfs.getReceiver(),
                mfs.getSenderid(),
                new Message(mfs.getMessage(),
                        mfs.getTimestamp()));
    }

    public void sendMessage(String message) {
        if (receiver == null) {
            showAlert("Выберите контакт для отправки сообщения", Alert.AlertType.ERROR);
            return;
        }
        MessageToServer MTS = new MessageToServer(receiver.getUid(), message);

        String jsonMessage = new Gson().toJson(MTS);
        try {
            conn.getChatClient().send(jsonMessage);

            dbService.addMessage(receiver.getUid(),
                    myUser.getUid(),
                    new Message(message, new Timestamp(System.currentTimeMillis()))
            );
            chatViewController.showMessage(myUser.getAccount_name(), message, new Timestamp(System.currentTimeMillis()), false);

        } catch (IOException ex) {
            showAlert("Потеряно соединение с сервером", Alert.AlertType.ERROR);
            controllerLogger.error(ex);
        }

    }

    private void loadChat() {
        List<Message> converstation = dbService.getChat(myUser, receiver);
        chatViewController.clearMessageWebView();

        for (Message message :
                converstation) {
            chatViewController.showMessage(message.getSender().getAccount_name(), message.getText(), message.getTime(), false);
        }
    }

    public void disconnect() {
        if (conn != null) {
            conn.disconnect();
            conn = null;
        }
        if (dbService != null) {
            dbService.close();
            dbService = null;
        }
        instance = null;
        contactList = null;
        contactListOfCards = null;
    }

    private ContactListFromServer[] convertContactListFromServer(String jsonText) {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        return gson.fromJson(jsonText, ContactListFromServer[].class);
    }

    private void synchronizeContactList() {
        dbService = new DataBaseService(myUser);
        contactList = dbService.getAllUserId();
        contactListOfCards = new ArrayList<>();
        
        dbService.getAllUsers().forEach(user -> {
            if (user.getUid() != myUser.getUid()) {
                contactListOfCards.add(new CFXListElement(user));
            }
        });

        try {
            ServerResponse response = HTTPSRequest.getContacts(token);
            if (response != null) {
                List<User> contactsToRemoveFromDb = dbService.getAllUsers();
                contactsToRemoveFromDb.remove(myUser);
                for (ContactListFromServer entry : convertContactListFromServer(response.getResponseJson())) {
                    if (!contactList.contains(entry.getId())) {
                        //TODO: себя не должно быть в списке контактов - убрать костыль (после решения на сервере)
                        if (entry.getId() == myUser.getUid()) continue; //костыль, который надо будет убрать
                        User user = new User(entry.getId(), entry.getName(), entry.getEmail());
                        addContactToDB(user);
                    }
                    for (CFXListElement cont : contactListOfCards) {
                        if (cont.getUser().getUid() == entry.getId()) {
                            cont.setOnlineStatus(entry.isStatusOnline());
                            break;
                        }
                    }
                    //в списке останутся только контакты, которых нет на сервере
                    contactsToRemoveFromDb.remove(
                            new User(entry.getId(), entry.getName(), entry.getEmail()));
                }

                //удаляем из локальной базы контакты, которых нет в списке контактов на сервере
                if (!contactsToRemoveFromDb.isEmpty()) 
                    contactsToRemoveFromDb.forEach(entry -> removeContactFromDb(entry));
            }
        } catch (Exception e) {
            controllerLogger.error("HTTPSRequest.getContacts_error", e);
        }

        // проверяем, есть ли наш пользователь в БД
        User user = dbService.getUser(myUser.getUid());
        if (user == null) {
            dbService.insertUser(myUser);
        }
    }

    private User convertJSONToUser(String jsonText) {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        return gson.fromJson(jsonText, User.class);
    }

    private List<CFXListElement> convertJSONToCFXListElements(String jsonText) {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        List<CFXListElement> res = new ArrayList<>();
        try {
            Map<String, UserFromServer> users
                    = gson.fromJson(jsonText, new TypeToken<Map<String, UserFromServer>>() {
                    }.getType());
            if (users != null && users.size() > 0) {
                users.forEach((id, userFS) -> res.add(new CFXListElement(
                        new User(Long.parseLong(id), userFS.getAccount_name(), userFS.getEmail()))));
            }
        } catch (Exception e) {
            return convertJSONToCFXListElement(jsonText);
        }
        return res.isEmpty() ? null : res;
    }

    //костыль, т.к. список найденных и один контакт приходят в разном виде
    private List<CFXListElement> convertJSONToCFXListElement(String jsonText) {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        List<CFXListElement> res = new ArrayList<>(1);
        try {
            res.add(new CFXListElement(gson.fromJson(jsonText, User.class)));
        } catch (Exception e) {
            controllerLogger.error("HTTPSRequest.getUserByNameOrEmail_JsonParsError", e);
        }
        return res.isEmpty() ? null : res;
    }

    public Group getGroupInfo(String groupName){
        Group group = new Group();
        try {
            ServerResponse response = HTTPSRequest.getGroupInfo(groupName,token);
            switch (response.getResponseCode()){
                case 200:
                    System.out.println("получение информации о группе");
                    GsonBuilder builder = new GsonBuilder();
                    Gson gson = builder.create();
                    group = gson.fromJson(response.getResponseJson(), Group.class);
                    return group;
                case 400:
                    System.out.println("Проблемы с токеном");
                    break;
                case 404:
                    System.out.println("группа не найдена");
                    break;
                case 500:
                    System.out.println("ошибка сервера");
                    break;
                default:
                    System.out.println("другая ошибка");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return group;
    }

    public void joinGroup(String groupName){
        Group group = getGroupInfo(groupName);
        addUserGroup(group.getGid(), Long.toString(myUser.getUid()));
    }

    public void addGroup(String group_name){
        AddGroup addGroup = new AddGroup(group_name);
        String requestJSON = new Gson().toJson(addGroup);

        try {
            ServerResponse response = HTTPSRequest.addGroup(requestJSON, token);
            switch (response.getResponseCode()) {
                case 200:
                    showAlert("Группа" + group_name + "успешно создана", Alert.AlertType.INFORMATION);
                    //addContactToDB(convertJSONToUser(response.getResponseJson()));
                    //if (chatViewController != null) chatViewController.fillContactListView();
                    break;
                case 400:
                    showAlert("Ошибка запроса", Alert.AlertType.ERROR);
                    break;
                case 404:
                    showAlert("Невозможно создать группу с названием: " + group_name, Alert.AlertType.ERROR);
                    break;
                case 500:
                    showAlert("Ошибка сервера", Alert.AlertType.ERROR);
                    break;
                default:
                    showAlert("Общая ошибка!", Alert.AlertType.ERROR);
            }
        } catch (Exception e) {
            controllerLogger.error("HTTPSRequest.addGroup_error", e);
        }

    }

    public void addUserGroup(String group_id, String new_user_id) {
        AddUserGroup aug = new AddUserGroup(group_id, new_user_id);
        String requestJSON = new Gson().toJson(aug);

        try {
            ServerResponse response = HTTPSRequest.addUserGroup(requestJSON, token);
            switch (response.getResponseCode()) {
                case 200:
                    showAlert("Группа успешно добавлена", Alert.AlertType.INFORMATION);
                    //addContactToDB(convertJSONToUser(response.getResponseJson()));
                    //if (chatViewController != null) chatViewController.fillContactListView();
                    break;
                case 400:
                    showAlert("Ошибка запроса", Alert.AlertType.ERROR);
                    break;
                case 404:
                    showAlert("Группа не найдена или вы уже состоите в ней", Alert.AlertType.ERROR);
                    break;
                case 500:
                    showAlert("Ошибка сервера", Alert.AlertType.ERROR);
                    break;
                default:
                    showAlert("Общая ошибка!", Alert.AlertType.ERROR);
            }
        } catch (Exception e) {
            controllerLogger.error("HTTPSRequest.addUserGroup_error", e);
        }
    }

    public boolean addContact(String contact) {
        UserToServer cts = new UserToServer(contact);
        String requestJSON = new Gson().toJson(cts);

        try {
            ServerResponse response = HTTPSRequest.addContact(requestJSON, token);
            switch (response.getResponseCode()) {
                case 201:
                    showAlert("Контакт " + contact + " успешно добавлен", Alert.AlertType.INFORMATION);
                    User newUser = convertJSONToUser(response.getResponseJson());
                    return addContactToDbAndChat(newUser);
                case 404:
                   // showAlert("Пользователь с email: " + contact + " не найден", Alert.AlertType.ERROR);
                    break;
                case 409:
                  //  showAlert("Пользователь " + contact + " уже есть в списке ваших контактов", Alert.AlertType.ERROR);
                    break;
                default:
                    //showAlert("Общая ошибка!", Alert.AlertType.ERROR);
            }
        } catch (Exception e) {
            controllerLogger.error("HTTPSRequest.addContact_error", e);
        }
        return false;
    }

    public List<CFXListElement> findContact(String contact) {
        UserToServer cts = new UserToServer(contact);
        String requestJSON = new Gson().toJson(cts);

        try {
            ServerResponse response = HTTPSRequest.getUser(contact, token);
            switch (response.getResponseCode()) {
                case 200:
                    return convertJSONToCFXListElements(response.getResponseJson());
                case 404:
                   // showAlert("Пользователь с email: " + contact + " не найден", Alert.AlertType.ERROR);
                    break;
                case 409:
                  //  showAlert("Пользователь " + contact + " уже есть в списке ваших контактов", Alert.AlertType.ERROR);
                    break;
                default:
                    //showAlert("Общая ошибка!", Alert.AlertType.ERROR);
            }
        } catch (Exception e) {
            controllerLogger.error("HTTPSRequest.getUserByNameOrEmail_error", e);
        }
        return null;
    }

    private void addContactToDB(User contact) {
        dbService.insertUser(new User(contact.getUid(), contact.getAccount_name(), contact.getEmail()));
        contactList.add(contact.getUid());
        contactListOfCards.add(new CFXListElement(contact));
    }

    private boolean addContactToDbAndChat(User contact) {
        addContactToDB(contact);
        if (chatViewController != null) {
            chatViewController.updateContactListView();
        }
        return true;
    }

    public boolean removeContact(String contactsEmail) {
        UserToServer cts = new UserToServer(contactsEmail);
        String requestJSON = new Gson().toJson(cts);

        try {
            ServerResponse response = HTTPSRequest.deleteContact(requestJSON, token);
            switch (response.getResponseCode()) {
                case 200:
                    showAlert("Контакт " + contactsEmail + " успешно удалён", Alert.AlertType.INFORMATION);
                    User delUser = new UserDeletedFromServer(response.getResponseJson()).toUser();
                    delUser.setEmail(contactsEmail); // на 08.01.2018 нет в ответе сервера
                    removeContactFromDbAndChat(delUser);
                    return true;
                case 404:
                    showAlert("Пользователя " + contactsEmail + " нет в списке контактов", Alert.AlertType.INFORMATION);
                    break;
                default:
                    //showAlert("Общая ошибка!", Alert.AlertType.ERROR);
            }
        } catch (Exception e) {
            controllerLogger.error("HTTPSRequest.addContact_error", e);
        }
        return false;
    }

    public void clearMessagesWithUser(User contact) {
        if (!dbService.getChat(myUser, contact).isEmpty())
            dbService.deleteChat(myUser, contact);
    }

    private void removeContactFromDb(User contact) {
        clearMessagesWithUser(contact);
        dbService.deleteUser(contact);
        contactList.remove(contact.getUid());
        contactListOfCards.remove(new CFXListElement(contact));
    }

    private void removeContactFromDbAndChat(User contact) {
        removeContactFromDb(contact);
        if (chatViewController != null) {
            chatViewController.updateContactListView();
            if (receiver.equals(contact)) chatViewController.clearMessageWebView();
        }
    }

    public void proceedRegister(String login, String password, String email) {
        String requestJSON = "{" +
                "\"account_name\": \"" + login + "\"," +
                "\"email\": \"" + email + "\"," +
                "\"password\": \"" + password + "\"" +
                "}";
        try {
            int responseCode = HTTPSRequest.registration(requestJSON);
            if (responseCode == 201) {
                showAlert("Вы успешно зарегистрированы", Alert.AlertType.INFORMATION);
            } else
                showAlert("Ошибка регистрации, код: " + responseCode, Alert.AlertType.ERROR);
        } catch (Exception e) {
            controllerLogger.error("HTTPSRequest.registration", e);
        }
    }

    public boolean proceedLogIn(String login, String password) {
        return authentication(login, password);
    }

    public List<String> getAllUserNames() {
        return dbService.getAllUserNames();
    }

    public String proceedRestorePassword(String email) {
        String answer = "0";
        String requestJSON = "{" +
                "\"email\": \"" + email + "\"" +
                "}";
        try {
            answer = HTTPSRequest.restorePassword(requestJSON);
        } catch (Exception e) {
            controllerLogger.error("proceedRestorePassword_error", e);
        }
        return answer;
    }

    public String proceedChangePassword(String email, String codeRecovery, String password) {
        String answer = "0";
        String requestJSON = "{" +
                "\"email\": \"" + email + "\"," +
                "\"code\": \"" + codeRecovery + "\"," +
                "\"password\": \"" + password + "\"" +
                "}";
        try {
            answer = HTTPSRequest.changePassword(requestJSON);
        } catch (Exception e) {
            controllerLogger.error("proceedChangePassword_error", e);
        }
        return answer;
    }
}
