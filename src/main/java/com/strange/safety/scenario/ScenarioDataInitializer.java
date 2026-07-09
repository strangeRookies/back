package com.strange.safety.scenario;

import com.strange.safety.scenario.entity.Scenario;
import com.strange.safety.scenario.entity.ScenarioParams;
import com.strange.safety.scenario.entity.ScenarioType;
import com.strange.safety.scenario.repository.ScenarioParamsRepository;
import com.strange.safety.scenario.repository.ScenarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ScenarioDataInitializer implements ApplicationRunner {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioParamsRepository scenarioParamsRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (scenarioRepository.count() > 0) return;

        init(ScenarioType.FALL_BED, "침대 낙상 감지", 5, 0.7f, null);
        init(ScenarioType.COLLAPSE,  "쓰러짐 감지",   3, 0.8f, null);
        init(ScenarioType.SYNCOPE,   "실신 감지",     3, 0.8f, null);
        init(ScenarioType.EXIT,      "이탈 감지",    10, 0.6f, "00:00-08:00");
        init(ScenarioType.ASSAULT,   "폭행 감지",     2, 0.9f, null);
        init(ScenarioType.HAZARD_ZONE,"위험구역 침범",  0, 0.5f, null);
    }

    private void init(ScenarioType type, String desc, int timeSec, float motion, String timeRestriction) {
        Scenario scenario = scenarioRepository.save(
                Scenario.builder().scenarioType(type).description(desc).build());
        scenarioParamsRepository.save(
                ScenarioParams.builder()
                        .scenario(scenario)
                        .timeThresholdSec(timeSec)
                        .motionThreshold(motion)
                        .timeRestriction(timeRestriction)
                        .build());
    }
}
