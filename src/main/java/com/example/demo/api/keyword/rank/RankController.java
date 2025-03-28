package com.example.demo.api.keyword.rank;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/rank")
@RequiredArgsConstructor
public class RankController {

    private final RankService rankService;

    @GetMapping("")
    public ResponseEntity<String> getRankByCategoryId(@RequestParam("categoryId")String categoryId) throws Exception{

        RankDTO rankDTO = rankService.getRankByCategoryId(categoryId);

        if(rankDTO == null){
            log.warn(">>>>>>> [getStatusById] No status found for rank: {}", categoryId);
            return ResponseEntity.notFound().build();
        }else{
            return ResponseEntity.ok(rankDTO.getRankKeyword());
        }
    }
}
