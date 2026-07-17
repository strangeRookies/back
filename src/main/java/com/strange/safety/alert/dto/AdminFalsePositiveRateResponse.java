package com.strange.safety.alert.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminFalsePositiveRateResponse {
    /** 최근 24시간 오탐률(%). 해당 구간에 이벤트가 하나도 없으면 null. */
    private Double ratePercent;
    /** ratePercent - 직전 24시간 오탐률(%). 음수면 개선. 두 구간 중 하나라도 데이터 없으면 null. */
    private Double deltaPercent;
    /** ratePercent 계산에 쓰인 최근 24시간 이벤트 수. */
    private long sampleSize;
}
