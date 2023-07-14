package nu.marginalia.control.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.control.model.FileStorageBaseWithStorage;
import nu.marginalia.control.model.ProcessHeartbeat;
import nu.marginalia.control.model.ServiceHeartbeat;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorage;
import nu.marginalia.db.storage.model.FileStorageBase;
import nu.marginalia.db.storage.model.FileStorageBaseId;
import nu.marginalia.db.storage.model.FileStorageId;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ControlFileStorageService {
    private final HikariDataSource dataSource;
    private final FileStorageService fileStorageService;

    @Inject
    public ControlFileStorageService(HikariDataSource dataSource, FileStorageService fileStorageService) {
        this.dataSource = dataSource;
        this.fileStorageService = fileStorageService;
    }

    @SneakyThrows
    public List<FileStorageBaseWithStorage> getStorageList() {
        Map<FileStorageBaseId, FileStorageBase> fileStorageBaseByBaseId = new HashMap<>();
        Map<FileStorageBaseId, List<FileStorage>> fileStoragByBaseId = new HashMap<>();

        List<FileStorageId> storageIds = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var storageByIdStmt = conn.prepareStatement("SELECT ID FROM FILE_STORAGE")) {
            var rs = storageByIdStmt.executeQuery();
            while (rs.next()) {
                storageIds.add(new FileStorageId(rs.getLong("ID")));
            }
        }

        for (var id : storageIds) {
            var storage = fileStorageService.getStorage(id);
            fileStorageBaseByBaseId.computeIfAbsent(storage.base().id(), k -> storage.base());
            fileStoragByBaseId.computeIfAbsent(storage.base().id(), k -> new ArrayList<>()).add(storage);
        }

        List<FileStorageBaseWithStorage> result = new ArrayList<>();
        for (var baseId : fileStorageBaseByBaseId.keySet()) {
            result.add(new FileStorageBaseWithStorage(fileStorageBaseByBaseId.get(baseId), fileStoragByBaseId.get(baseId)));
        }

        return result;
    }


}
