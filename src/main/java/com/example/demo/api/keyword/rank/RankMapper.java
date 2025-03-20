package com.example.demo.api.keyword.rank;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RankMapper {

    Rank getRankByCategoryId(String categoryId);

}
