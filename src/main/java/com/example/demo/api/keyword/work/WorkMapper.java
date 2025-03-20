package com.example.demo.api.keyword.work;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkMapper {

    List<Work> getAllWork();

    void insertWork(Work work);

    Work getWorkById(int id);

    void updateWork(Work work);

    void changeStatusCode(Integer id);


}
