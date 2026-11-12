package com.rishank.securedigitallocker.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.rishank.securedigitallocker.model.ActivityLog;
import com.rishank.securedigitallocker.model.Document;
import com.rishank.securedigitallocker.model.User;
import com.rishank.securedigitallocker.repository.ActivityLogRepository;
import com.rishank.securedigitallocker.repository.UserRepository;

@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository,
                              UserRepository userRepository) {
        this.activityLogRepository = activityLogRepository;
        this.userRepository = userRepository;
    }

    public void record(String action, String email, Document document) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User account was not found"));
        activityLogRepository.save(new ActivityLog(action, user, document));
    }

    public List<ActivityLog> recentForOwner(String email) {
        return activityLogRepository.findTop20ByUserEmailOrderByTimestampDesc(email);
    }
}
