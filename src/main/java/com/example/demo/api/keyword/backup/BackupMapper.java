package com.example.demo.api.keyword.backup;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BackupMapper {

    void insertBackup(Backup backup);

    void updateBackup(Backup backup);

    List<Backup> getBackupsByWorkId(Integer workId);
}
