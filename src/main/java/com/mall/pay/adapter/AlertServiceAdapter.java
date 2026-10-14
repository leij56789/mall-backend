package com.mall.pay.adapter;

import com.mall.pay.port.AlertPort;
import com.mall.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// com.mall.pay.adapter.AlertServiceAdapter（在业务系统里）
@Component
@RequiredArgsConstructor
public class AlertServiceAdapter implements AlertPort {
    private final AlertService alertService;   // 业务系统的 AlertService

    @Override public void alert(String t, String c) { alertService.sendAlert(t, c); }
    @Override public void urgent(String t, String c) { alertService.sendUrgentAlert(t, c); }
    @Override public void critical(String t, String c, Throwable e) { alertService.sendCriticalAlert(t, c, e); }
}