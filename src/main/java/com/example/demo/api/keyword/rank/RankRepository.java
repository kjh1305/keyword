package com.example.demo.api.keyword.rank;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RankRepository {

    private final RankMapper rankMapper;

    public Rank getRankByCategoryId(String categoryId) {
        return rankMapper.getRankByCategoryId(categoryId);
    }
}
