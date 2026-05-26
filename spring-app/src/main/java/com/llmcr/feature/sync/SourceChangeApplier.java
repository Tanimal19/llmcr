package com.llmcr.feature.sync;

import com.llmcr.domain.entity.Chunk;
import com.llmcr.domain.entity.ChunkCollection;
import com.llmcr.domain.entity.Source;
import com.llmcr.domain.entity.TrackRoot;
import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.domain.repository.SourceRepository;
import com.llmcr.feature.sync.SourceSyncService.SourcePreview;
import com.llmcr.feature.sync.SourceSyncService.SyncStatus;
import com.llmcr.infrastructure.vectorstore.MyVectorStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SourceChangeApplier {

    private final SourceRepository sourceRepository;
    private final MyVectorStore vectorStore;

    public SourceChangeApplier(SourceRepository sourceRepository, MyVectorStore vectorStore) {
        this.sourceRepository = sourceRepository;
        this.vectorStore = vectorStore;
    }

    public Source applySourceSyncStatus(TrackRoot trackRoot, SourcePreview sourcePreview) {
        SyncStatus syncStatus = sourcePreview.syncStatus();
        return switch (syncStatus) {
            case ADDED -> {
                addSource(trackRoot, sourcePreview);
                yield null;
            }
            case REMOVED -> requireExistingSource(sourcePreview);
            case MODIFIED -> {
                updateModifiedSource(sourcePreview);
                yield null;
            }
            case SYNCED -> null;
            default -> throw new APIServiceException(
                    APIServiceException.ErrorCode.SOURCE_SYNC_TRACK_ROOT_FAILED,
                    "Unhandled sync status: " + syncStatus);
        };
    }

    public void batchRemoveSources(List<Source> sources) {
        if (sources.isEmpty()) {
            return;
        }
        batchRemoveSourceChunks(sources);
        sourceRepository.deleteAll(sources);
    }

    private Source requireExistingSource(SourcePreview preview) {
        Source source = resolveExistingSource(preview);
        if (source == null) {
            throw new APIServiceException(
                    APIServiceException.ErrorCode.SOURCE_SYNC_TRACK_ROOT_FAILED,
                    "Source not found: " + preview.path());
        }
        return source;
    }

    private void addSource(TrackRoot trackRoot, SourcePreview sourcePreview) {
        Source newSource = new Source(
                sourcePreview.path(),
                LocalSourceScanner.computeContentHash(Path.of(sourcePreview.path())),
                sourcePreview.type());
        trackRoot.addSource(newSource);
        sourceRepository.save(newSource);
    }

    private void updateModifiedSource(SourcePreview sourcePreview) {
        Source existingSource = requireExistingSource(sourcePreview);
        existingSource.setContentHash(LocalSourceScanner.computeContentHash(Path.of(sourcePreview.path())));
        existingSource.getContexts().clear();
        existingSource.setExtracted(false);
        sourceRepository.save(existingSource);
    }

    private Source resolveExistingSource(SourcePreview preview) {
        if (preview.id() != null) {
            return sourceRepository.findById(preview.id()).orElse(null);
        }
        return sourceRepository.findByPath(preview.path());
    }

    private void batchRemoveSourceChunks(List<Source> sources) {
        try {
            List<Chunk> chunks = sources
                    .stream()
                    .flatMap(source -> source.getContexts().stream())
                    .flatMap(context -> context.getChunks().stream())
                    .toList();

            if (chunks.isEmpty()) {
                return;
            }

            Map<String, Set<Long>> collectionToChunkIds = new HashMap<>();
            for (Chunk chunk : chunks) {
                Long chunkId = chunk.getId();
                if (chunkId == null) {
                    continue;
                }

                for (ChunkCollection chunkCollection : chunk.getChunkCollections()) {
                    String collectionName = chunkCollection.getName();
                    if (collectionName == null) {
                        continue;
                    }
                    collectionToChunkIds.computeIfAbsent(collectionName, key -> new HashSet<>()).add(chunkId);
                }
            }

            for (Map.Entry<String, Set<Long>> entry : collectionToChunkIds.entrySet()) {
                vectorStore.removeChunks(new ArrayList<>(entry.getValue()), entry.getKey());
            }

            for (Chunk chunk : chunks) {
                for (ChunkCollection chunkCollection : new ArrayList<>(chunk.getChunkCollections())) {
                    chunkCollection.removeChunk(chunk);
                }
            }
        } catch (Exception ex) {
            throw new APIServiceException(APIServiceException.ErrorCode.SOURCE_SYNC_REMOVE_CHUNKS_FAILED, ex);
        }
    }
}
