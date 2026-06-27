package id.zahra.studymate;

import org.junit.Test;

import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;

public class ExampleUnitTest {
    @Test
    public void levelProgression_usesThreeHundredXpPerLevel() {
        assertEquals(1, GameRules.levelForXp(0));
        assertEquals(1, GameRules.levelForXp(299));
        assertEquals(2, GameRules.levelForXp(300));
        assertEquals(7, GameRules.levelForXp(1800));
        assertEquals(10, GameRules.levelForXp(2700));
    }

    @Test
    public void characterRank_evolvesAtAllMilestones() {
        assertEquals("Novice Scholar", GameRules.rankForLevel(1));
        assertEquals("Quest Apprentice", GameRules.rankForLevel(2));
        assertEquals("Deadline Knight", GameRules.rankForLevel(3));
        assertEquals("Knowledge Mage", GameRules.rankForLevel(4));
        assertEquals("Legendary Scholar", GameRules.rankForLevel(5));
        assertEquals("Mythic Mentor", GameRules.rankForLevel(7));
        assertEquals("Celestial Sage", GameRules.rankForLevel(10));
    }

    @Test
    public void questReward_followsPriority() {
        assertEquals(100, GameRules.rewardForPriority("Prioritas Tinggi"));
        assertEquals(60, GameRules.rewardForPriority("Prioritas Sedang"));
        assertEquals(35, GameRules.rewardForPriority("Prioritas Rendah"));
    }

    @Test
    public void deadlineReminder_onlyTargetsUpcomingThreeDays() {
        long now=1_000_000L;
        assertTrue(GameRules.shouldNotifyDeadline(now,now+TimeUnit.DAYS.toMillis(3),"Belum Dikerjakan"));
        assertTrue(GameRules.shouldNotifyDeadline(now,now+TimeUnit.HOURS.toMillis(5),"Sedang Dikerjakan"));
        assertFalse(GameRules.shouldNotifyDeadline(now,now+TimeUnit.DAYS.toMillis(4),"Belum Dikerjakan"));
        assertFalse(GameRules.shouldNotifyDeadline(now,now-TimeUnit.HOURS.toMillis(1),"Belum Dikerjakan"));
        assertFalse(GameRules.shouldNotifyDeadline(now,now+TimeUnit.DAYS.toMillis(1),"Selesai"));
    }
}
