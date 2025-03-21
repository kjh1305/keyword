package com.example.demo.api.keyword.rank;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 네이버 쇼핑인사이트
 */
@Service
@RequiredArgsConstructor
public class RankService {

    private final RankRepository rankRepository;

    public RankDTO getRankByCategoryId(String categoryId) throws Exception{
        Rank rank = rankRepository.getRankByCategoryId(categoryId);

        if (rank == null){
            return null;
        }

        return RankDTO.builder().rankKeyword(rank.getRankKeyword()).build();
    }

}
