package com.parroto.content.service;

import com.parroto.content.dto.*;
import com.parroto.content.entity.Lesson;
import com.parroto.content.entity.Sentence;
import com.parroto.content.exception.ApiException;
import com.parroto.content.repository.LessonRepository;
import com.parroto.content.repository.SentenceRepository;
import com.parroto.content.service.storage.AudioFormats;
import com.parroto.content.service.storage.LessonAudioStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LessonService {

    private static final Duration LIST_TTL = Duration.ofHours(24);
    private static final Duration DETAIL_TTL = Duration.ofHours(24);

    private final LessonRepository lessonRepository;
    private final SentenceRepository sentenceRepository;
    private final CacheService cacheService;
    private final LessonAudioStorage audioStorage;

    public LessonService(LessonRepository lessonRepository, SentenceRepository sentenceRepository,
                          CacheService cacheService, LessonAudioStorage audioStorage) {
        this.lessonRepository = lessonRepository;
        this.sentenceRepository = sentenceRepository;
        this.cacheService = cacheService;
        this.audioStorage = audioStorage;
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonSummaryResponse> listLessons(String cefrLevel, String topicTag, int page, int limit) {
        String cacheKey = "lessons:list:%s:%s:%d:%d".formatted(cefrLevel, topicTag, page, limit);
        PageResponse<LessonSummaryResponse> cached = cacheService.get(cacheKey, PageResponse.class);
        if (cached != null) {
            return cached;
        }
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Lesson> lessons = lessonRepository.search(blankToNull(cefrLevel), blankToNull(topicTag), pageable);
        Page<LessonSummaryResponse> mapped = lessons.map(l ->
                LessonSummaryResponse.from(l, sentenceRepository.countByLesson_Id(l.getId())));
        PageResponse<LessonSummaryResponse> response = PageResponse.of(mapped, page, limit);
        cacheService.set(cacheKey, response, LIST_TTL);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonSummaryResponse> searchLessons(String query, int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), limit);
        Page<Lesson> lessons = lessonRepository.fullTextSearch(query, pageable);
        Page<LessonSummaryResponse> mapped = lessons.map(l ->
                LessonSummaryResponse.from(l, sentenceRepository.countByLesson_Id(l.getId())));
        return PageResponse.of(mapped, page, limit);
    }

    @Transactional(readOnly = true)
    public LessonDetailResponse getLesson(UUID id) {
        String cacheKey = "lesson:" + id;
        LessonDetailResponse cached = cacheService.get(cacheKey, LessonDetailResponse.class);
        if (cached != null) {
            return cached;
        }
        Lesson lesson = findActiveLesson(id);
        List<SentenceResponse> sentences = sentenceRepository.findByLessonIdOrderBySentenceOrderAsc(id).stream()
                .map(SentenceResponse::from)
                .collect(Collectors.toList());
        LessonDetailResponse response = LessonDetailResponse.from(lesson, sentences);
        cacheService.set(cacheKey, response, DETAIL_TTL);
        return response;
    }

    @Transactional
    public LessonDetailResponse createLesson(CreateLessonRequest request) {
        Lesson lesson = Lesson.builder()
                .title(request.title())
                .sourceUrl(request.sourceUrl())
                .cefrLevel(request.cefrLevel())
                .topicTag(request.topicTag())
                .description(request.description())
                .audioUrl(request.audioUrl())
                .duration(request.duration())
                .build();
        // saveAndFlush (not save): the response DTO below reads @CreationTimestamp/@UpdateTimestamp
        // fields, which Hibernate only populates in-memory once the INSERT actually executes.
        lesson = lessonRepository.saveAndFlush(lesson);
        evictLessonCaches(lesson.getId());
        return LessonDetailResponse.from(lesson, List.of());
    }

    @Transactional
    public List<SentenceResponse> addSentences(UUID lessonId, CreateSentencesRequest request) {
        Lesson lesson = findActiveLesson(lessonId);
        for (SentenceInput input : request.sentences()) {
            if (input.startMs() < 0 || input.startMs() >= input.endMs()) {
                throw ApiException.badRequest("Each sentence needs startMs >= 0 and startMs < endMs");
            }
        }
        int order = sentenceRepository.findByLessonIdOrderBySentenceOrderAsc(lessonId).stream()
                .map(Sentence::getSentenceOrder)
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .orElse(0) + 1;
        List<Sentence> toSave = new ArrayList<>();
        for (SentenceInput input : request.sentences()) {
            toSave.add(Sentence.builder()
                    .lesson(lesson)
                    .sentenceOrder(order++)
                    .text(input.text())
                    .ipa(input.ipa())
                    .translationVi(input.translationVi())
                    .startMs(input.startMs())
                    .endMs(input.endMs())
                    .audioUrl(input.audioUrl())
                    .imageUrl(input.imageUrl())
                    .build());
        }
        List<Sentence> saved = sentenceRepository.saveAll(toSave);
        evictLessonCaches(lessonId);
        return saved.stream().map(SentenceResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public LessonDetailResponse updateLesson(UUID id, UpdateLessonRequest request) {
        Lesson lesson = findActiveLesson(id);
        if (request.title() != null && !request.title().isBlank()) {
            lesson.setTitle(request.title());
        }
        if (request.description() != null) {
            lesson.setDescription(request.description());
        }
        if (request.topicTag() != null) {
            lesson.setTopicTag(request.topicTag());
        }
        if (request.cefrLevel() != null && !request.cefrLevel().isBlank()) {
            lesson.setCefrLevel(request.cefrLevel());
        }
        lesson = lessonRepository.saveAndFlush(lesson);
        evictLessonCaches(id);
        List<SentenceResponse> sentences = sentenceRepository.findByLessonIdOrderBySentenceOrderAsc(id).stream()
                .map(SentenceResponse::from).collect(Collectors.toList());
        return LessonDetailResponse.from(lesson, sentences);
    }

    @Transactional
    public void deleteLesson(UUID id) {
        Lesson lesson = findActiveLesson(id);
        lesson.setIsDeleted(true);
        lessonRepository.save(lesson);
        evictLessonCaches(id);
    }

    /**
     * ADM-03: partial update of a sentence that must belong to the lesson (otherwise 404).
     * Changing {@code sentenceOrder} moves the sentence and renumbers the lesson 1..N.
     */
    @Transactional
    public SentenceResponse updateSentence(UUID lessonId, UUID sentenceId, UpdateSentenceRequest request) {
        findActiveLesson(lessonId);
        List<Sentence> ordered = sentenceRepository.findByLessonIdOrderBySentenceOrderAsc(lessonId);
        Sentence target = findInLesson(ordered, sentenceId);

        if (request.text() != null) {
            if (request.text().isBlank()) {
                throw ApiException.badRequest("Nội dung câu không được để trống");
            }
            target.setText(request.text().trim());
        }
        if (request.ipa() != null) {
            target.setIpa(blankToNull(request.ipa()));
        }
        if (request.translationVi() != null) {
            target.setTranslationVi(blankToNull(request.translationVi()));
        }
        if (request.audioUrl() != null) {
            target.setAudioUrl(blankToNull(request.audioUrl()));
        }
        if (request.imageUrl() != null) {
            target.setImageUrl(blankToNull(request.imageUrl()));
        }
        Integer startMs = request.startMs() != null ? request.startMs() : target.getStartMs();
        Integer endMs = request.endMs() != null ? request.endMs() : target.getEndMs();
        if (startMs != null && endMs != null && startMs >= endMs) {
            throw ApiException.badRequest("startMs phải nhỏ hơn endMs");
        }
        target.setStartMs(startMs);
        target.setEndMs(endMs);

        if (request.sentenceOrder() != null) {
            sentenceRepository.saveAll(SentenceOrdering.move(ordered, target, request.sentenceOrder()));
        } else {
            sentenceRepository.save(target);
        }
        evictLessonCaches(lessonId);
        return SentenceResponse.from(target);
    }

    /** ADM-03: deletes a sentence of the lesson and renumbers the remaining ones 1..N in the same transaction. */
    @Transactional
    public void deleteSentence(UUID lessonId, UUID sentenceId) {
        findActiveLesson(lessonId);
        List<Sentence> ordered = sentenceRepository.findByLessonIdOrderBySentenceOrderAsc(lessonId);
        Sentence target = findInLesson(ordered, sentenceId);
        sentenceRepository.delete(target);
        sentenceRepository.flush();
        sentenceRepository.saveAll(SentenceOrdering.without(ordered, target));
        evictLessonCaches(lessonId);
    }

    @Transactional
    public UploadAudioResponse uploadLessonAudio(UUID id, MultipartFile file, Integer duration) {
        AudioFormats.validateUpload(file);
        if (duration != null && duration < 0) {
            throw ApiException.badRequest("duration phải >= 0");
        }
        Lesson lesson = findActiveLesson(id);
        String oldUrl = lesson.getAudioUrl();
        String url = audioStorage.store(file, "lessons/" + id);
        lesson.setAudioUrl(url);
        if (duration != null) {
            lesson.setDuration(duration);
        }
        lessonRepository.save(lesson);
        evictLessonCaches(id);
        if (oldUrl != null && !oldUrl.equals(url)) {
            // Remove the replaced file only once the new URL is committed.
            afterCommit(() -> audioStorage.delete(oldUrl));
        }
        return new UploadAudioResponse(url, lesson.getDuration());
    }

    /**
     * LES-07: drops {@code lesson:{id}} and every {@code lessons:list:*} page (sentence counts, titles…).
     * Search results are not cached, so there is no search key to evict. Runs after commit when a
     * transaction is active so a concurrent read cannot re-cache pre-commit data.
     */
    public void evictLessonCaches(UUID lessonId) {
        afterCommit(() -> {
            cacheService.invalidateByPattern("lesson:" + lessonId);
            cacheService.invalidateByPattern("lessons:list:*");
        });
    }

    @Transactional(readOnly = true)
    public Lesson findActiveLesson(UUID id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Lesson not found"));
        if (Boolean.TRUE.equals(lesson.getIsDeleted())) {
            throw ApiException.notFound("Lesson not found");
        }
        return lesson;
    }

    private static Sentence findInLesson(List<Sentence> ordered, UUID sentenceId) {
        return ordered.stream()
                .filter(s -> sentenceId.equals(s.getId()))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy câu trong bài học"));
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
