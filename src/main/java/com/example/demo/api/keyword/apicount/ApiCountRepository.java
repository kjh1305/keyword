package com.example.demo.api.keyword.apicount;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class ApiCountRepository {

    private final ApiCountMapper apiCountMapper;

    public Integer save(ApiCount apiCount){
        return apiCountMapper.save(apiCount);
    }

    public ApiCount findById(Integer id){
        return apiCountMapper.findById(id);
    }
}
