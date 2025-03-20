package com.example.demo.api.keyword.work;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkService {

    private final WorkRepository workRepository;

    //모든 work 조회
    public List<Work> getAllWork(){
        return workRepository.getAllWork();
    }

    //work 생성
    public void insertWork(Work work){
        workRepository.insertWork(work);
    }

    //id로 work 조회
    public Work getWorkById(int id){
        return workRepository.getWorkById(id);
    }

    //work 수정
    public void updateWork(Work work){
        workRepository.updateWork(work);
    }

    public void changeStatusCode(Integer id){
        workRepository.changeStatusCode(id);
    }
}
