package com.example.demo.api.status;


import com.example.demo.api.keyword.work.Work;
import com.example.demo.api.keyword.work.WorkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
public class StatusController {

    private final StatusService statusService;
    private final WorkService workService;

    //status 생성
    @PostMapping("/")
    public ResponseEntity<Long> insertStatus(@RequestBody Status status) throws Exception {
        log.info(">>>>>>> [save] status = {}",status);
        Long id = statusService.saveOrUpdateStatus(status);
        return ResponseEntity.ok(id);
    }

    //id를 통해 status 조회
    @GetMapping("/{id}")
    public ResponseEntity<Status> getStatusById(@PathVariable Long id) {
        log.info(">>>>>>> [read] Request to get status by ID: {}", id);
        Status status = statusService.getStatusById(id);
        if (status == null) {
            log.warn(">>>>>>> [getStatusById] No status found for ID: {}", id);
//            return ResponseEntity.notFound().build();
            throw new IllegalArgumentException("해당 ID를 가진 상태가 없습니다.: " + id);
        }
        return ResponseEntity.ok(status);
    }

    //모든 status 조회
    @GetMapping("/")
    public ResponseEntity<List<Status>> getAllStatus() throws Exception {
        log.info(">>>>>>> [read] Request to get all statuses");
        List<Status> statuses = statusService.getAllStatus();
        return ResponseEntity.ok(statuses);
    }

    @PutMapping("/kill/{id}")
    public ResponseEntity<Long> killStatus(@PathVariable long id) throws Exception {
        log.info(">>>>>>> [update] Request to kill status with ID: {}", id);
        Status status = statusService.getStatusById(id);
        if (status == null) {
            log.warn(">>>>>>> [update] Status not found for ID: {}", id);
            return ResponseEntity.notFound().build(); // HTTP 404 Not Found
        }
        status.setStatusCode(-9);

        Work work = workService.getWorkById((int)id);
        if (work == null) {
            log.warn(">>>>>>> [update] Work not found for ID: {}", id);
            return ResponseEntity.notFound().build(); // HTTP 404 Not Found
        }
        work.setStatusCode(-9);
        workService.updateWork(work);

        Long updatedId = statusService.saveOrUpdateStatus(status);
        return ResponseEntity.ok(updatedId);
    }
}
