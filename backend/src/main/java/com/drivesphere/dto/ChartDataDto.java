package com.drivesphere.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartDataDto {

    private List<String> labels;
    private List<DatasetDto> datasets;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DatasetDto {
        private String label;
        private List<Number> data;
        private String borderColor;
        private String backgroundColor;
        private Boolean fill;
        private Double tension;
    }
}
