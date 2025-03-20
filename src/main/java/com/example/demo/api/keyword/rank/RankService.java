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

    public Rank getRankByCategoryId(String categoryId) throws Exception{
        return rankRepository.getRankByCategoryId(categoryId);
    }

}
