package com.mall.controller;

import com.mall.common.Result;
import com.mall.dto.response.SeckillResponse;
import com.mall.service.SeckillBookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillBookService seckillBookService;

    @PostMapping("/{bookId}")
    public Result<SeckillResponse> seckill(@PathVariable Long bookId, Integer quantity) {
        SeckillResponse seckillResponse =seckillBookService.seckill(bookId,quantity);
        return Result.success(seckillResponse);
    }
}