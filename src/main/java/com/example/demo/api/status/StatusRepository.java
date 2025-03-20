package com.example.demo.api.status;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatusRepository extends CrudRepository<Status,Long> {
    List<Status> findAll();
}
