package com.example.demo.api.keyword.rank;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RankRepository {

    private final RankMapper rankMapper;

    public Rank getRankByCategoryId(String categoryId) {
        return rankMapper.getRankByCategoryId(categoryId);
    }
}
