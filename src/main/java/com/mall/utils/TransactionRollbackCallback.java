package com.mall.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 事务回滚回调工具
 * 在事务回滚时自动执行注册的 Redis 回滚逻辑
 */
@Slf4j
public class TransactionRollbackCallback {

    private static final ThreadLocal<List<RollbackAction>> ROLLBACK_ACTIONS = new ThreadLocal<>();

    /**
     * 注册一个回滚回调（在事务回滚时执行）
     */
    public static void registerRollbackAction(RollbackAction action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("当前线程没有活动事务，无法注册回滚回调");
            return;
        }

        List<RollbackAction> actions = ROLLBACK_ACTIONS.get();
        if (actions == null) {
            actions = new ArrayList<>();
            ROLLBACK_ACTIONS.set(actions);
        }
        actions.add(action);

        // 注册事务同步（只注册一次）
        if (actions.size() == 1) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        try {
                            List<RollbackAction> pendingActions = ROLLBACK_ACTIONS.get();
                            if (pendingActions != null && !pendingActions.isEmpty()) {
                                // 只有事务回滚时才执行（STATUS_ROLLED_BACK = 1）
                                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                                    log.info("事务回滚，执行 {} 个回滚回调", pendingActions.size());
                                    for (RollbackAction action : pendingActions) {
                                        try {
                                            action.execute();
                                        } catch (Exception e) {
                                            log.error("回滚回调执行失败", e);
                                        }
                                    }
                                } else {
                                    log.debug("事务提交，跳过回滚回调");
                                }
                            }
                        } finally {
                            ROLLBACK_ACTIONS.remove();
                        }
                    }
                }
            );
        }
    }

    /**
     * 回滚动作接口
     */
    @FunctionalInterface
    public interface RollbackAction {
        void execute();
    }
}