package com.example.demo.api.keyword.apicount;


import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApiCountMapper {

    Integer save(ApiCount apiCount);

    ApiCount findById(Integer id);

}
