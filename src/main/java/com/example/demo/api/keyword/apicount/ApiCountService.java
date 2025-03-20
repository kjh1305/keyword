package com.example.demo.api.keyword.apicount;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCountService {

    private static final int DEFAULT_API_COUNT_ID = 1;

    private final ApiCountRepository apiCountRepository;

    public void updateApiCount(ApiCount apiCount){
        apiCountRepository.save(apiCount);
    }

    public ApiCount getApiCountById(Integer id){
        return apiCountRepository.findById(id);
    }

    //12시에 api 사용횟수를 0으로 초기화
    //NOTE : 추후에 여러 스케쥴을 사용하려면 @EnableAsync, @Async 사용해야함(비동기로 바꿔야함)
    @Scheduled(cron = "0 0 0 * * *")
    public void resetApiCount() {
        log.info("API 사용 횟수 초기화 작업 시작");
        try {
            ApiCount apiCount = apiCountRepository.findById(DEFAULT_API_COUNT_ID);
            if (apiCount != null) {
                apiCount.setUseCount(0);
                apiCountRepository.save(apiCount);
                log.info("API 사용 횟수가 초기화되었습니다.");
            } else {
                log.warn("ID가 1인 ApiCount를 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            log.error("API 사용 횟수 초기화 중 오류가 발생했습니다.", e);
        }
        log.info("API 사용 횟수 초기화 작업 종료");
    }
}
