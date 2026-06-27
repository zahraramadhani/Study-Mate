package id.zahra.studymate;

import java.util.concurrent.TimeUnit;

/** Pure RPG progression rules, kept Android-free so they can be unit tested. */
final class GameRules {
    private GameRules() {}

    static int levelForXp(int xp) {
        return Math.max(1, Math.max(0, xp) / 300 + 1);
    }

    static String rankForLevel(int level) {
        if (level >= 10) return "Celestial Sage";
        if (level >= 7) return "Mythic Mentor";
        if (level >= 5) return "Legendary Scholar";
        if (level == 4) return "Knowledge Mage";
        if (level == 3) return "Deadline Knight";
        if (level == 2) return "Quest Apprentice";
        return "Novice Scholar";
    }

    static int rewardForPriority(String priority) {
        if (priority != null && priority.contains("Tinggi")) return 100;
        if (priority != null && priority.contains("Sedang")) return 60;
        return 35;
    }

    static boolean shouldNotifyDeadline(long now,long deadline,String status){
        long remaining=deadline-now;
        return !"Selesai".equals(status)&&remaining>0&&remaining<=TimeUnit.DAYS.toMillis(3);
    }
}
