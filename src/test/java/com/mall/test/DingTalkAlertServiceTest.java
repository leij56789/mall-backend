package com.mall.test;

import com.mall.service.DingTalkAlertService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
@Slf4j
@SpringBootTest
public class DingTalkAlertServiceTest {

    @Autowired
    private DingTalkAlertService dingTalkAlertService;

    @Test
    public void testSendAlert() throws InterruptedException {
        log.info("测试DingTalkAlertService开始");
//        dingTalkAlertService.sendTextAlert("【测试】这是一条从 Spring 服务发送的告警消息");
        dingTalkAlertService.sendTextAlert("测试service");
        log.info("测试DingTalkAlertService结束");
        // 异步发送需要等待一下
        Thread.sleep(3000);
    }
}