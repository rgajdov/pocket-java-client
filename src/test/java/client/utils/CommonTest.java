package client.utils;

import org.junit.Assert;
import org.junit.Test;

public class CommonTest {

    @Test
    public void urlToHyperlink() {
        String actualURL = "yandex.ru";
        String expectedURL = "<a href=\"yandex.ru\">yandex.ru</a>";
        Assert.assertEquals(Common.urlToHyperlink(actualURL), expectedURL);
    }
}