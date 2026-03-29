package com.kova.aiticketinghub.notification.repository;

import com.kova.aiticketinghub.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
