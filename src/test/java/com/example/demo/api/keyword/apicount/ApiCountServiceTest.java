package com.example.demo.api.keyword.apicount;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class ApiCountServiceTest {

    @Autowired
    private ApiCountService apiCountService;

    @Test
    void updateApiCount() {
    }

    @Test
    @DisplayName("API Count 조회 테스트")
    void getApiCountById() {
        ApiCount apiCountById = apiCountService.getApiCountById(1);
        Assertions.assertNotNull(apiCountById);
        Assertions.assertEquals(1, apiCountService.getApiCountById(1).getId());
    }

    @Test
    void resetApiCount() {
    }
}