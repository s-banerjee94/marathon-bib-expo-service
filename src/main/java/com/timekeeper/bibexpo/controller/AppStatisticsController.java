package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.response.EventStatisticsResponse;
import com.timekeeper.bibexpo.model.dto.response.OrganizationStatisticsResponse;
import com.timekeeper.bibexpo.model.dto.response.UserStatisticsResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.AppStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for role-scoped statistics endpoints.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AppStatisticsController implements AppStatisticsControllerApi {

    private final AppStatisticsService appStatisticsService;

    @Override
    public ResponseEntity<UserStatisticsResponse> getUserStatistics(User currentUser) {
        log.info("GET /statistics/users by: {} role: {}", currentUser.getUsername(), currentUser.getRole());
        return ResponseEntity.ok(appStatisticsService.getUserStatistics(currentUser));
    }

    @Override
    public ResponseEntity<UserStatisticsResponse> refreshUserStatistics(User currentUser) {
        log.info("POST /statistics/users/refresh by: {} role: {}", currentUser.getUsername(), currentUser.getRole());
        return ResponseEntity.ok(appStatisticsService.refreshUserStatistics(currentUser));
    }

    @Override
    public ResponseEntity<OrganizationStatisticsResponse> getOrganizationStatistics(User currentUser) {
        log.info("GET /statistics/organizations by: {} role: {}", currentUser.getUsername(), currentUser.getRole());
        return ResponseEntity.ok(appStatisticsService.getOrganizationStatistics(currentUser));
    }

    @Override
    public ResponseEntity<OrganizationStatisticsResponse> refreshOrganizationStatistics(User currentUser) {
        log.info("POST /statistics/organizations/refresh by: {} role: {}", currentUser.getUsername(), currentUser.getRole());
        return ResponseEntity.ok(appStatisticsService.refreshOrganizationStatistics(currentUser));
    }

    @Override
    public ResponseEntity<EventStatisticsResponse> getEventStatistics(User currentUser) {
        log.info("GET /statistics/events by: {} role: {}", currentUser.getUsername(), currentUser.getRole());
        return ResponseEntity.ok(appStatisticsService.getEventStatistics(currentUser));
    }

    @Override
    public ResponseEntity<EventStatisticsResponse> refreshEventStatistics(User currentUser) {
        log.info("POST /statistics/events/refresh by: {} role: {}", currentUser.getUsername(), currentUser.getRole());
        return ResponseEntity.ok(appStatisticsService.refreshEventStatistics(currentUser));
    }
}
