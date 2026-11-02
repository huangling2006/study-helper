package com.hl.study.wrong.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class HotTop100VO {
    private List<HotWrongVO> items;
    private LocalDateTime generatedAt;
}
