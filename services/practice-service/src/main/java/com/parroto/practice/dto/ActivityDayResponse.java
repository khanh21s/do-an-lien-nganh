package com.parroto.practice.dto;

/**
 * One heatmap cell (PRA-03): a calendar day in Asia/Ho_Chi_Minh.
 *
 * @param date     ISO date "YYYY-MM-DD"
 * @param attempts number of practice attempts that day
 * @param minutes  round(sum(duration seconds) / 60)
 */
public record ActivityDayResponse(String date, long attempts, long minutes) {
}
