package com.codingshuttle.distributed_lovable.account_service.config;

import com.codingshuttle.distributed_lovable.account_service.entity.Plan;
import com.codingshuttle.distributed_lovable.account_service.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanSeeder implements ApplicationRunner {

    private final PlanRepository planRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (planRepository.count() > 0) {
            return;
        }

        Plan free = new Plan();
        free.setName("Free");
        free.setStripePriceId("free");
        free.setMaxProjects(3);
        free.setMaxTokensPerDay(20000);
        free.setMaxPreviews(1);
        free.setUnlimitedAi(false);
        free.setActive(true);

        Plan pro = new Plan();
        pro.setName("Pro");
        pro.setStripePriceId("price_pro_placeholder");
        pro.setMaxProjects(50);
        pro.setMaxTokensPerDay(500000);
        pro.setMaxPreviews(10);
        pro.setUnlimitedAi(true);
        pro.setActive(true);

        planRepository.save(free);
        planRepository.save(pro);
        log.info("Seeded default plans: Free, Pro");
    }
}
