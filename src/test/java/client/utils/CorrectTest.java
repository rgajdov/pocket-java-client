package client.utils;

import org.junit.Assert;
import org.junit.Test;

public class CorrectTest {

    @Test
    public void isValidEmail() {
        Assert.assertTrue(Correct.isValidEmail("rgajdov@gmail.com"));
    }

    @Test
    public void checkPasswordStrength() {
        String password = "qweRty@234";
        int testExpected = Correct.checkPasswordStrength(password);
        int testActual = 5;
        Assert.assertEquals("Степень надежности пароля: " + testExpected + " из " + testActual, testExpected, testActual);
    }
}