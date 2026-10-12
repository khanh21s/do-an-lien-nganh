package com.parroto.content.service;

import com.parroto.content.dto.*;
import com.parroto.content.entity.Lesson;
import com.parroto.content.entity.Sentence;
import com.parroto.content.exception.ApiException;
import com.parroto.content.repository.LessonRepository;
import com.parroto.content.repository.SentenceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LessonService {

    private static final Duration LIST_TTL = Duration.ofHours(24);
    private static final Duration DETAIL_TTL = Duration.ofHours(24);

    private final LessonRepository lessonRepository;
    private final SentenceRepository sentenceRepository;
    private final CacheService cacheService;
    private final S3StorageService s3StorageService;

    public LessonService(LessonRepository lessonRepository, SentenceRepository sentenceRepository,
                          CacheService cacheService, S3StorageService s3StorageService) {
        this.lessonRepository = lessonRepository;
        this.sentenceRepository = sentenceRepository;
        this.cacheService = cacheService;
        this.s3StorageService = s3StorageService;
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
        invalidateListCache();
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
        int order = sentenceRepository.findByLessonIdOrderBySentenceOrderAsc(lessonId).size() + 1;
        List<Sentence> toSave = new java.util.ArrayList<>();
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
        cacheService.invalidateByPattern("lesson:" + lessonId);
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
        invalidateListCache();
        cacheService.invalidateByPattern("lesson:" + id);
        List<SentenceResponse> sentences = sentenceRepository.findByLessonIdOrderBySentenceOrderAsc(id).stream()
                .map(SentenceResponse::from).collect(Collectors.toList());
        return LessonDetailResponse.from(lesson, sentences);
    }

    @Transactional
    public void deleteLesson(UUID id) {
        Lesson lesson = findActiveLesson(id);
        lesson.setIsDeleted(true);
        lessonRepository.save(lesson);
        invalidateListCache();
        cacheService.invalidateByPattern("lesson:" + id);
    }

    @Transactional
    public UploadAudioResponse uploadLessonAudio(UUID id, org.springframework.web.multipart.MultipartFile file, Integer duration) {
        Lesson lesson = findActiveLesson(id);
        s3StorageService.deleteByUrl(lesson.getAudioUrl());
        String url = s3StorageService.uploadAudio(file, "lessons/" + id);
        lesson.setAudioUrl(url);
        if (duration != null) {
            lesson.setDuration(duration);
        }
        lessonRepository.save(lesson);
        cacheService.invalidateByPattern("lesson:" + id);
        invalidateListCache();
        return new UploadAudioResponse(url, lesson.getDuration());
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

    private void invalidateListCache() {
        cacheService.invalidateByPattern("lessons:list:*");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
