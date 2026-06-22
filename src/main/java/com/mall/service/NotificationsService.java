package com.mall.service;

import org.springframework.stereotype.Component;

public interface NotificationsService {
    Boolean saveNotification(String username,String message);
}
