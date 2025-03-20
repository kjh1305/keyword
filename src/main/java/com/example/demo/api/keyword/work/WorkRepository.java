package com.example.demo.api.keyword.work;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class WorkRepository {

    final WorkMapper workMapper;

    public List<Work> getAllWork(){
        return workMapper.getAllWork();
    }

    public void insertWork(Work work){
        workMapper.insertWork(work);
    }

    public Work getWorkById(int id){
        return workMapper.getWorkById(id);
    }

    public void updateWork(Work work){
        workMapper.updateWork(work);
    }

    public void changeStatusCode(Integer id){
        workMapper.changeStatusCode(id);
    }
}
