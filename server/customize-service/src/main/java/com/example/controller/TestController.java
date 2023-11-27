package com.example.controller;

import com.example.common.ApiResponse;
import com.example.dto.TestDto;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * @program: cook-frame
 * @description:
 * @author: 星哥
 * @create: 2023-06-30
 **/
@RestController
@RequestMapping("/test")
@Api(tags = "test", description = "测试")
public class TestController {
    
    @ApiOperation(value = "测试")
    @RequestMapping(value = "/test", method = RequestMethod.POST)
    public ApiResponse<Long> test(@Valid @RequestBody TestDto testDto) {
        if (testDto.getSleepTime() != null) {
            try {
                Thread.sleep(testDto.getSleepTime());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return ApiResponse.ok(testDto.getId());
    }
    
    @ApiOperation(value = "测试V2")
    @RequestMapping(value = "/testV2", method = RequestMethod.POST)
    public ApiResponse<Long> testV2(@Valid @RequestBody TestDto testDto) {
        if (testDto.getSleepTime() != null) {
            try {
                Thread.sleep(testDto.getSleepTime());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return ApiResponse.ok(testDto.getId());
    }
}
