package com.example.demo.api.keyword.work;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/work")
@RequiredArgsConstructor
public class WorkController {

    private final WorkService workService;

    @GetMapping("")
    public ResponseEntity<List<Work>> getAllWork() {
        List<Work> allWork = workService.getAllWork();
        return ResponseEntity.ok(allWork);
    }

    @PutMapping("/statuscode/{id}")
    public void changeStatusCode(@PathVariable Integer id){
        log.info("삭제 요청 완료");
        workService.changeStatusCode(id);
    }

}
