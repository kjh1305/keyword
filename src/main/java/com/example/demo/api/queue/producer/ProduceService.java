package com.example.demo.api.queue.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProduceService {

    @Value("${spring.rabbitmq.topic-exchange-name}")
    private String TOPIC_EXCHANGE_NAME;

    private final RabbitTemplate rabbitTemplate;

    //큐에 파일 작업 전송
    public void sendExtractWorkToQueue(int workId,int sellerCountMin,int sellerCountMax,int searchCount,String useKipris) throws Exception{
        try {
            HashMap<String, Object> queueObject = new HashMap<>();
            queueObject.put("workId", workId);
            queueObject.put("sellerCountMin",sellerCountMin);
            queueObject.put("sellerCountMax",sellerCountMax);
            queueObject.put("searchCount",searchCount);
            //키프리스 사용여부(테스트 환경에서만 적용)
            queueObject.put("useKipris",useKipris);

            log.info("메세지 전송 직전");
            rabbitTemplate.convertAndSend(TOPIC_EXCHANGE_NAME, "foo.bar.baz", queueObject);
            log.info("메세지 전송 직후");
            log.info("전송 완료");
        } catch (Exception e) {
            log.error("서버오류!!!", e);
        }
    }
}
