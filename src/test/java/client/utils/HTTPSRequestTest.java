package client.utils;

import client.model.ServerResponse;
import client.model.formatMsgWithServer.AuthFromServer;
import client.model.formatMsgWithServer.AuthToServer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.*;

public class HTTPSRequestTest {

    private String token;

    @Before
    public void beforeTests() throws Exception {
        // Для большинства операций с сервером требуется токен - получаем его до начала всех тестов
        // (для авторизации вводим логин и пароль любого реального юзера)
        String userLogin = "tester2";
        String userPassword = "123";
        AuthToServer ATS = new AuthToServer(userLogin, userPassword);
        String requestString = new Gson().toJson(ATS);
        String response = HTTPSRequest.authorization(requestString);
        if (response.contains("token")) {
            GsonBuilder builder = new GsonBuilder();
            Gson gson = builder.create();
            AuthFromServer AFS = gson.fromJson(response, AuthFromServer.class);
            token = AFS.token;
        }
        Assert.assertNotNull("Login or password incorrect!", token);
    }

    @Test()
    public void getUser() throws Exception {
        int userId = 51; // id тестируемого юзера
        ServerResponse response = HTTPSRequest.getUser(userId, token);
        Assert.assertNotEquals("Token not found!", 401, response.getResponseCode());
        Assert.assertNotEquals("Unauthorized!", 403, response.getResponseCode());
        Assert.assertNotEquals("User not found!", 404, response.getResponseCode());
    }

    @Test()
    public void getContacts() throws Exception {
        ServerResponse response = HTTPSRequest.getContacts(token);
        Assert.assertNotEquals("Token not found!", 401, response.getResponseCode());
        Assert.assertNotEquals("Unauthorized!", 403, response.getResponseCode());
    }

}