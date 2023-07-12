package com.example.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @program: toolkit
 * @description:
 * @author: k
 * @create: 2023-06-30
 **/
@Data
@ApiModel(value="DepthRuleDto", description ="深度规则")
public class DepthRuleDto {
    
    @ApiModelProperty(name ="startTimeWindow", dataType ="String", value ="开始时间窗口", required =true)
    @NotBlank
    private String startTimeWindow;
    
    @ApiModelProperty(name ="endTimeWindow", dataType ="String", value ="结束时间窗口", required =true)
    @NotBlank
    private String endTimeWindow;
    
    @ApiModelProperty(name ="statTime", dataType ="Integer", value ="统计时间", required =true)
    @NotNull
    private Integer statTime;
    
    @ApiModelProperty(name ="statTimeType", dataType ="Integer", value ="统计时间类型 1:秒 2:分钟", required =true)
    @NotNull
    private Integer statTimeType;
    
    @ApiModelProperty(name ="threshold", dataType ="Integer", value ="阈值", required =true)
    @NotNull
    private Integer threshold;
    
    @ApiModelProperty(name ="effectiveTime", dataType ="Integer", value ="规则生效限制时间", required =true)
    @NotNull
    private Integer effectiveTime;
    
    @ApiModelProperty(name ="effectiveTimeType", dataType ="Integer", value ="规则生效限制时间类型 1:秒 2:分钟", required =true)
    @NotNull
    private Integer effectiveTimeType;
    
    private String limitApi;
    
    @ApiModelProperty(name ="message", dataType ="String", value ="提示信息")
    private String message;
    
    @ApiModelProperty(name ="status", dataType ="Integer", value ="状态 1正常 0失效")
    private Integer status;
}
