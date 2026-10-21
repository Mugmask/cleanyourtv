package com.gathin.tvhome;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

/** The screensaver's short date: no locale dots, each word capitalised. */
public class ClockSceneDateTest {

    private static Date october21() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.OCTOBER, 21);
        return c.getTime();
    }

    @Test
    public void spanishDropsTheDotsAndCapitalises() {
        // Spanish abbreviations come as "mié." and "oct.".
        assertEquals("Mié 21 Oct", ClockScene.shortDate(october21(), new Locale("es", "AR")));
    }

    @Test
    public void englishKeepsItsOwnCapitals() {
        assertEquals("Wed 21 Oct", ClockScene.shortDate(october21(), Locale.US));
    }
}
