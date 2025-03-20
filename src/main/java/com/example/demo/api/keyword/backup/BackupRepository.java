package com.example.demo.api.keyword.backup;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class BackupRepository {

    final private BackupMapper backupMapper;

    public void insertBackup(Backup backup){
        backupMapper.insertBackup(backup);
    }

    public void updateBackup(Backup backup){
        backupMapper.updateBackup(backup);
    }

    //work id로 backup 리스트 가져오기
    public List<Backup> getBackupsByWorkId(Integer workId){
        return backupMapper.getBackupsByWorkId(workId);
    }
}
