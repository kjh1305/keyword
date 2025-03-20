package com.example.demo.api.keyword.backup;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BackupService {

    private final BackupRepository backupRepository;

    //백업 생성
    //추출작업 맨처음에 생성
    public void insertBackup(Backup backup){
        backupRepository.insertBackup(backup);
    }

    //한 칼럼 작업 완료시에 업데이트
    public void updateBackup(Backup backup){
        backupRepository.updateBackup(backup);
    }

    //work id로 backup 리스트 가져오기
    public List<Backup> getBackupsByWorkId(Integer workId){
        return backupRepository.getBackupsByWorkId(workId);
    }
}
