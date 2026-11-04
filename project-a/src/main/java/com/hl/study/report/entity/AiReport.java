package com.hl.study.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_report")
public class AiReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String requestId;
    private Integer status;
    private String content;
    private Integer tokenCost;
    private String model;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
}
