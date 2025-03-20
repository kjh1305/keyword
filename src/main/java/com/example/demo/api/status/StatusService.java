package com.example.demo.api.status;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatusService {

    private final StatusRepository statusRepository;

    //status 생성
    @Transactional
    public Long saveOrUpdateStatus(Status status) throws Exception{
        return statusRepository.save(status).getId();
    }

    //id로 status 조회
    public Status getStatusById(Long id){
        //해당 id의 status 값 존재 유무 체크
        return statusRepository.findById(id).orElse(null);
    }

    //모든 status 조회
    public List<Status> getAllStatus() throws Exception{
        return statusRepository.findAll();
    }

    @Transactional
    public void deleteStatus(Status status) throws Exception{
        if (!statusRepository.existsById(status.getId())) {
            throw new IllegalArgumentException("Status with id " + status.getId() + " does not exist");
        }
        statusRepository.delete(status);
    }
}
