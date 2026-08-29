package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.entity.PaymentRefundRecord;
import com.mall.enums.RefundStatus;
import com.mall.mapper.PaymentRefundRecordMapper;
import com.mall.pay.dto.RefundRecordQueryRequest;
import com.mall.pay.dto.RefundRecordQueryResponse;
import com.mall.service.PaymentRefundRecordQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundRecordQueryServiceImpl implements PaymentRefundRecordQueryService {

    private final PaymentRefundRecordMapper refundRecordMapper;

    @Override
    public RefundRecordQueryResponse queryRefundRecords(RefundRecordQueryRequest request) {
        LambdaQueryWrapper<PaymentRefundRecord> wrapper = buildQueryWrapper(request);

        // 按创建时间降序
        wrapper.orderByDesc(PaymentRefundRecord::getCreatedAt);

        // 分页查询
        Page<PaymentRefundRecord> page = new Page<>(request.getPageNum(), request.getPageSize());
        IPage<PaymentRefundRecord> pageResult = refundRecordMapper.selectPage(page, wrapper);

        // 转换结果
        List<RefundRecordQueryResponse.RefundRecordInfo> records = new ArrayList<>();
        for (PaymentRefundRecord record : pageResult.getRecords()) {
            records.add(convertToInfo(record));
        }

        return RefundRecordQueryResponse.builder()
                .total(pageResult.getTotal())
                .pageNum(request.getPageNum())
                .pageSize(request.getPageSize())
                .records(records)
                .build();
    }

    @Override
    public List<RefundRecordQueryResponse.RefundRecordInfo> queryByPaymentId(String paymentId) {
        if (!StringUtils.hasText(paymentId)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<PaymentRefundRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentRefundRecord::getPaymentId, paymentId)
                .orderByDesc(PaymentRefundRecord::getCreatedAt);
        List<PaymentRefundRecord> records = refundRecordMapper.selectList(wrapper);
        List<RefundRecordQueryResponse.RefundRecordInfo> result = new ArrayList<>();
        for (PaymentRefundRecord record : records) {
            result.add(convertToInfo(record));
        }
        return result;
    }

    @Override
    public RefundRecordQueryResponse.RefundRecordInfo queryByOutRequestNo(String outRequestNo) {
        if (!StringUtils.hasText(outRequestNo)) {
            return null;
        }
        PaymentRefundRecord record = refundRecordMapper.selectByOutRequestNo(outRequestNo);
        return record != null ? convertToInfo(record) : null;
    }

    @Override
    public List<PaymentRefundRecord> queryProcessingTimeout(int limit) {
        return refundRecordMapper.selectProcessingTimeout(limit);
    }

    // ===== 私有方法 =====

    private LambdaQueryWrapper<PaymentRefundRecord> buildQueryWrapper(RefundRecordQueryRequest request) {
        LambdaQueryWrapper<PaymentRefundRecord> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(request.getPaymentId())) {
            wrapper.eq(PaymentRefundRecord::getPaymentId, request.getPaymentId());
        }
        if (StringUtils.hasText(request.getOutTradeNo())) {
            wrapper.eq(PaymentRefundRecord::getOutTradeNo, request.getOutTradeNo());
        }
        if (StringUtils.hasText(request.getOutRequestNo())) {
            wrapper.eq(PaymentRefundRecord::getOutRequestNo, request.getOutRequestNo());
        }
        if (request.getStatusList() != null && !request.getStatusList().isEmpty()) {
            wrapper.in(PaymentRefundRecord::getStatus, request.getStatusList());
        }
        if (request.getStartTime() != null) {
            wrapper.ge(PaymentRefundRecord::getCreatedAt, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            wrapper.le(PaymentRefundRecord::getCreatedAt, request.getEndTime());
        }
        if (StringUtils.hasText(request.getMinAmount())) {
            wrapper.ge(PaymentRefundRecord::getRefundAmount, new BigDecimal(request.getMinAmount()));
        }
        if (StringUtils.hasText(request.getMaxAmount())) {
            wrapper.le(PaymentRefundRecord::getRefundAmount, new BigDecimal(request.getMaxAmount()));
        }

        return wrapper;
    }

    private RefundRecordQueryResponse.RefundRecordInfo convertToInfo(PaymentRefundRecord record) {
        RefundStatus status = RefundStatus.fromCode(record.getStatus());
        return RefundRecordQueryResponse.RefundRecordInfo.builder()
                .id(record.getId())
                .paymentId(record.getPaymentId())
                .outTradeNo(record.getOutTradeNo())
                .tradeNo(record.getTradeNo())
                .refundAmount(record.getRefundAmount())
                .refundReason(record.getRefundReason())
                .outRequestNo(record.getOutRequestNo())
                .status(record.getStatus())
                .statusDesc(status != null ? status.getDesc() : record.getStatus())
                .failReason(record.getFailReason())
                .thirdPartyRefundNo(record.getThirdPartyRefundNo())
                .retryCount(record.getRetryCount())
                .nextQueryTime(record.getNextQueryTime())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}