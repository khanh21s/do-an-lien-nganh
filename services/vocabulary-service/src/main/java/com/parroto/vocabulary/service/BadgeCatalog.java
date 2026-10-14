package com.parroto.vocabulary.service;

/** Badges of the "Dịch nhanh" game (T16); earned once, checked when a round finishes. */
public enum BadgeCatalog {
    FIRST_GAME("🎮", "Khởi động", "Hoàn thành ván đầu tiên"),
    COMBO_10("🔥", "Nóng tay", "Đạt combo 10 trong một ván"),
    COMBO_20("💥", "Combo 20", "Đạt combo 20 trong một ván"),
    SPEED_10("⚡", "Tốc độ ánh sáng", "10 câu đúng dưới 3 giây (không gợi ý) trong một ván"),
    SCORE_500("🏅", "Cao thủ", "Ghi 500 điểm trong một ván"),
    B2_MASTER("🎓", "Bậc thầy B2", "Trả lời đúng 100 từ B2"),
    SURVIVOR_30("🛡️", "Người sống sót", "Trả lời đúng 30 câu trong một ván Sinh tồn"),
    DAILY_PERFECT("💯", "Hoàn hảo", "Đúng cả 10 câu Thử thách hằng ngày"),
    DAILY_7("📅", "Chuyên cần", "Hoàn thành 7 Thử thách hằng ngày");

    private final String icon;
    private final String title;
    private final String description;

    BadgeCatalog(String icon, String title, String description) {
        this.icon = icon;
        this.title = title;
        this.description = description;
    }

    public String icon() {
        return icon;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }
}
